package com.uisep.slideapi.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.xml.sax.InputSource;

@RestController
@RequestMapping("/api/v1/proxy")
@CrossOrigin(origins = "*")
@Slf4j
@Hidden
public class OdooProxyController {

    private static final String COMMON = "https://app.universidadisep.com/xmlrpc/2/common";
    private static final String OBJECT = "https://app.universidadisep.com/xmlrpc/2/object";
    private static final String DB     = "UisepFinal";
    private static final String USER   = "iallamadas@universidadisep.com";
    private static final String PASS   = "Veronica023_";

    private static final List<String> FIELDS = List.of(
        "id", "name", "slide_type", "channel_id",
        "html_content", "html_embed_code", "use_html_embed",
        "bunny_url", "url", "external_url", "youtube_id",
        "preconverthtml", "active", "is_published", "total_views", "description"
    );

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static volatile Integer cachedUid = null;

    @GetMapping("/odoo/{id}")
    public ResponseEntity<Map<String, Object>> fromOdoo(@PathVariable int id) {
        long t0 = System.currentTimeMillis();
        try {
            int uid = ensureUid();
            String xml = callRead(uid, id);
            Map<String, Object> result = parseStruct(xml);
            if (result.isEmpty()) return ResponseEntity.notFound().build();
            result.put("_fetchTimeMs", System.currentTimeMillis() - t0);
            result.put("_source", "odoo_xmlrpc");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Odoo proxy slide {}: {}", id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "_fetchTimeMs", System.currentTimeMillis() - t0
            ));
        }
    }

    private int ensureUid() throws Exception {
        if (cachedUid != null) return cachedUid;
        String xml = "<?xml version='1.0'?><methodCall><methodName>authenticate</methodName><params>"
            + "<param><value><string>" + DB   + "</string></value></param>"
            + "<param><value><string>" + USER + "</string></value></param>"
            + "<param><value><string>" + PASS + "</string></value></param>"
            + "<param><value><struct/></value></param></params></methodCall>";

        Document doc = parse(post(COMMON, xml));
        NodeList ints = doc.getElementsByTagName("int");
        if (ints.getLength() == 0) throw new RuntimeException("Auth failed");
        cachedUid = Integer.parseInt(ints.item(0).getTextContent().trim());
        log.info("Odoo proxy authenticated, uid={}", cachedUid);
        return cachedUid;
    }

    private String callRead(int uid, int slideId) throws Exception {
        StringBuilder f = new StringBuilder();
        for (String field : FIELDS)
            f.append("<value><string>").append(field).append("</string></value>");

        String xml = "<?xml version='1.0'?><methodCall><methodName>execute_kw</methodName><params>"
            + "<param><value><string>" + DB   + "</string></value></param>"
            + "<param><value><int>"    + uid  + "</int></value></param>"
            + "<param><value><string>" + PASS + "</string></value></param>"
            + "<param><value><string>slide.slide</string></value></param>"
            + "<param><value><string>read</string></value></param>"
            + "<param><value><array><data>"
            + "<value><array><data><value><int>" + slideId + "</int></value></data></array></value>"
            + "<value><array><data>" + f + "</data></array></value>"
            + "</data></array></value></param>"
            + "<param><value><struct/></value></param></params></methodCall>";

        return post(OBJECT, xml);
    }

    private String post(String url, String xml) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "text/xml; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(xml))
            .timeout(Duration.ofSeconds(60))
            .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    private Document parse(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml)));
    }

    private Map<String, Object> parseStruct(String xmlBody) throws Exception {
        Document doc = parse(xmlBody);
        // Find the first <struct> inside the array result
        NodeList structs = doc.getElementsByTagName("struct");
        if (structs.getLength() == 0) return Map.of();

        Element struct = (Element) structs.item(0);
        Map<String, Object> result = new LinkedHashMap<>();

        NodeList children = struct.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element member)) continue;
            if (!"member".equals(member.getTagName())) continue;

            String name = null;
            Element valueEl = null;
            NodeList mc = member.getChildNodes();
            for (int j = 0; j < mc.getLength(); j++) {
                if (!(mc.item(j) instanceof Element e)) continue;
                if ("name".equals(e.getTagName()))  name = e.getTextContent();
                if ("value".equals(e.getTagName())) valueEl = e;
            }
            if (name != null && valueEl != null)
                result.put(name, extractValue(valueEl));
        }
        return result;
    }

    private Object extractValue(Element value) {
        NodeList children = value.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element e)) continue;
            return switch (e.getTagName()) {
                case "string"        -> e.getTextContent();
                case "int", "i4"   -> { try { yield Integer.parseInt(e.getTextContent().trim()); } catch (Exception ex) { yield 0; } }
                case "double"       -> Double.parseDouble(e.getTextContent().trim());
                case "boolean"      -> "1".equals(e.getTextContent().trim());
                case "nil"          -> null;
                case "array"        -> extractArray(e);
                case "struct"       -> extractNestedStruct(e);
                default              -> e.getTextContent();
            };
        }
        String text = value.getTextContent().trim();
        return text.isEmpty() ? null : text;
    }

    private List<Object> extractArray(Element arrayEl) {
        List<Object> list = new ArrayList<>();
        NodeList data = arrayEl.getElementsByTagName("data");
        if (data.getLength() == 0) return list;
        NodeList children = data.item(0).getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element e && "value".equals(e.getTagName()))
                list.add(extractValue(e));
        }
        return list;
    }

    private Map<String, Object> extractNestedStruct(Element structEl) {
        Map<String, Object> m = new LinkedHashMap<>();
        NodeList children = structEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element member) || !"member".equals(member.getTagName())) continue;
            String name = null; Element valueEl = null;
            NodeList mc = member.getChildNodes();
            for (int j = 0; j < mc.getLength(); j++) {
                if (!(mc.item(j) instanceof Element e)) continue;
                if ("name".equals(e.getTagName()))  name = e.getTextContent();
                if ("value".equals(e.getTagName())) valueEl = e;
            }
            if (name != null && valueEl != null) m.put(name, extractValue(valueEl));
        }
        return m;
    }

    @GetMapping("/odoo-status")
    public ResponseEntity<Map<String, Object>> odooStatus() {
        long t0 = System.currentTimeMillis();
        try {
            cachedUid = null; // force re-auth to test live connection
            int uid = ensureUid();
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "uid", uid,
                "user", USER,
                "server", "app.universidadisep.com",
                "db", DB,
                "authTimeMs", System.currentTimeMillis() - t0
            ));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                "ok", false,
                "error", e.getMessage(),
                "authTimeMs", System.currentTimeMillis() - t0
            ));
        }
    }
}
