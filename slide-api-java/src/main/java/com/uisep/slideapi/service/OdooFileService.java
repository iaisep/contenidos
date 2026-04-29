package com.uisep.slideapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Descarga archivos binarios (PDFs) desde el filestore de Odoo producción.
 * Los binarios de slide.slide están en ir.attachment con res_field='binary_content'.
 * URL de descarga: /web/content/{attachment_id}?download=1
 */
@Service
@Slf4j
public class OdooFileService {

    private static final String ODOO_URL = "https://app.universidadisep.com";
    private static final String DB       = "UisepFinal";
    private static final String USER     = "iallamadas@universidadisep.com";
    private static final String PASS     = "Veronica023_";

    @Value("${storage.files.path:/files}")
    private String filesPath;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String sessionId;
    private final Object sessionLock = new Object();

    // slideId → ir.attachment.id  (loaded once per JVM lifetime)
    private Map<Integer, Integer> slideAttachmentMap;
    private final Object mapLock = new Object();

    public OdooFileService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Returns the local path for a slide file. Downloads from Odoo if not present.
     * Returns empty if the slide has no binary content in Odoo.
     */
    public Optional<Path> downloadAndSave(int slideId) {
        Path filePath = localPath(slideId);
        if (isValidFile(filePath)) {
            return Optional.of(filePath);
        }

        Integer attId = getAttachmentId(slideId);
        if (attId == null) {
            return Optional.empty(); // slide has no binary in Odoo
        }

        try {
            ensureAuthenticated();
            byte[] content = fetchAttachment(attId, slideId);
            if (content == null || content.length == 0) {
                return Optional.empty();
            }
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, content);
            log.info("Slide {}: downloaded {} bytes (att {})", slideId, content.length, attId);
            return Optional.of(filePath);
        } catch (Exception e) {
            log.warn("Slide {}: download failed — {}", slideId, e.getMessage());
            return Optional.empty();
        }
    }

    public boolean hasRemoteFile(int slideId) {
        return getAttachmentId(slideId) != null;
    }

    public boolean exists(int slideId) {
        return isValidFile(localPath(slideId));
    }

    public Path localPath(int slideId) {
        return Paths.get(filesPath, slideId + ".bin");
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private Integer getAttachmentId(int slideId) {
        ensureMapLoaded();
        return slideAttachmentMap.get(slideId);
    }

    private void ensureMapLoaded() {
        if (slideAttachmentMap != null) return;
        synchronized (mapLock) {
            if (slideAttachmentMap != null) return;
            try {
                ensureAuthenticated();
                slideAttachmentMap = loadAttachmentMap();
                log.info("OdooFileService: loaded {} slide→attachment mappings", slideAttachmentMap.size());
            } catch (Exception e) {
                log.error("OdooFileService: failed to load attachment map — {}", e.getMessage());
                slideAttachmentMap = new HashMap<>();
            }
        }
    }

    private Map<Integer, Integer> loadAttachmentMap() throws Exception {
        // JSON-RPC call to ir.attachment.search_read
        String body = """
            {"jsonrpc":"2.0","method":"call","id":1,"params":{
              "model":"ir.attachment","method":"search_read",
              "args":[[["res_model","=","slide.slide"],["res_field","=","binary_content"]]],
              "kwargs":{"fields":["id","res_id","file_size"],"limit":0}
            }}
            """;

        HttpResponse<String> resp = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create(ODOO_URL + "/web/dataset/call_kw"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("Cookie", "session_id=" + sessionId)
                .timeout(Duration.ofSeconds(60))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode records = root.path("result");

        Map<Integer, Integer> map = new HashMap<>();
        if (records.isArray()) {
            for (JsonNode r : records) {
                int resId = r.path("res_id").asInt();
                int attId = r.path("id").asInt();
                if (resId > 0 && attId > 0) {
                    map.put(resId, attId);
                }
            }
        }
        return map;
    }

    private byte[] fetchAttachment(int attId, int slideId) throws Exception {
        HttpResponse<byte[]> resp = doGetAttachment(attId);

        if (resp.statusCode() == 401 || resp.statusCode() == 403 || isLoginPage(resp.body())) {
            synchronized (sessionLock) {
                sessionId = null;
                authenticate();
            }
            resp = doGetAttachment(attId);
        }

        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " for att " + attId);
        }
        return resp.body();
    }

    private HttpResponse<byte[]> doGetAttachment(int attId) throws Exception {
        return httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create(ODOO_URL + "/web/content/" + attId + "?download=1"))
                .header("Cookie", "session_id=" + sessionId)
                .GET()
                .timeout(Duration.ofMinutes(5))
                .build(),
            HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    private boolean isValidFile(Path path) {
        try {
            return Files.exists(path) && Files.size(path) > 100;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isLoginPage(byte[] body) {
        if (body == null || body.length < 50) return false;
        String preview = new String(body, 0, Math.min(300, body.length));
        return preview.contains("login") && preview.contains("<!DOCTYPE");
    }

    private void ensureAuthenticated() throws Exception {
        if (sessionId == null) {
            synchronized (sessionLock) {
                if (sessionId == null) authenticate();
            }
        }
    }

    private void authenticate() throws Exception {
        String body = String.format(
            "{\"jsonrpc\":\"2.0\",\"method\":\"call\",\"id\":1,\"params\":{\"db\":\"%s\",\"login\":\"%s\",\"password\":\"%s\"}}",
            DB, USER, PASS
        );

        HttpResponse<String> resp = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create(ODOO_URL + "/web/session/authenticate"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        sessionId = resp.headers().allValues("Set-Cookie").stream()
            .flatMap(h -> java.util.Arrays.stream(h.split(";")))
            .map(String::trim)
            .filter(s -> s.startsWith("session_id="))
            .map(s -> s.substring("session_id=".length()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No session_id in Odoo auth response"));

        log.info("OdooFileService: Odoo session obtained");
    }
}
