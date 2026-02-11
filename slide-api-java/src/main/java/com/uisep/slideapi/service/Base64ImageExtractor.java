package com.uisep.slideapi.service;

import com.uisep.slideapi.entity.processed.SlideImage;
import com.uisep.slideapi.repository.processed.SlideImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio para extraer y procesar imágenes Base64 del contenido HTML.
 * Convierte imágenes embebidas a registros almacenables y referencias URL.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class Base64ImageExtractor {
    
    private final SlideImageRepository slideImageRepository;
    
    // Patrón para detectar imágenes Base64 en HTML
    private static final Pattern BASE64_PATTERN = Pattern.compile(
        "data:image/(png|jpeg|jpg|gif|webp|bmp|svg\\+xml);base64,([A-Za-z0-9+/=]+)",
        Pattern.CASE_INSENSITIVE
    );
    
    // Patrón para tags <img> con src base64
    private static final Pattern IMG_TAG_PATTERN = Pattern.compile(
        "<img[^>]*src=[\"']?(data:image/[^\"'\\s>]+)[\"']?[^>]*>",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Resultado de la extracción de imágenes.
     */
    public record ExtractionResult(
        String cleanedHtml,
        List<SlideImage> extractedImages,
        int totalImagesFound,
        long originalSize,
        long cleanedSize
    ) {}
    
    /**
     * Extrae todas las imágenes Base64 del contenido HTML.
     * 
     * @param slideId ID del slide
     * @param htmlContent Contenido HTML con posibles imágenes Base64
     * @param baseUrl URL base para generar URLs públicas de las imágenes
     * @return Resultado con HTML limpio e imágenes extraídas
     */
    @Transactional("processedTransactionManager")
    public ExtractionResult extractAndProcess(Integer slideId, String htmlContent, String baseUrl) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return new ExtractionResult(htmlContent, Collections.emptyList(), 0, 0, 0);
        }
        
        long originalSize = htmlContent.length();
        List<SlideImage> extractedImages = new ArrayList<>();
        String cleanedHtml = htmlContent;
        int imageIndex = 0;
        int totalFound = 0;
        
        Matcher matcher = BASE64_PATTERN.matcher(htmlContent);
        
        // Recolectar todas las coincidencias primero
        List<Base64Match> matches = new ArrayList<>();
        while (matcher.find()) {
            totalFound++;
            String mimeType = "image/" + matcher.group(1).toLowerCase();
            String base64Data = matcher.group(2);
            String fullMatch = matcher.group(0);
            
            matches.add(new Base64Match(fullMatch, mimeType, base64Data));
        }
        
        log.info("Slide {}: Encontradas {} imágenes Base64", slideId, totalFound);
        
        // Procesar cada imagen
        for (Base64Match match : matches) {
            try {
                // Decodificar Base64
                byte[] imageData = Base64.getDecoder().decode(match.base64Data);
                
                // Calcular hash para deduplicación
                String hash = calculateHash(imageData);
                
                // Verificar si ya existe
                Optional<SlideImage> existingImage = slideImageRepository.findByImageHash(hash);
                
                SlideImage image;
                if (existingImage.isPresent()) {
                    // Reutilizar imagen existente
                    image = existingImage.get();
                    log.debug("Slide {}: Imagen duplicada detectada (hash: {})", slideId, hash);
                } else {
                    // Crear nueva imagen
                    String extension = getExtension(match.mimeType);
                    String filename = String.format("slide_%d_img_%d.%s", slideId, imageIndex, extension);
                    String publicUrl = String.format("%s/api/v1/images/%d", baseUrl, slideId) + "/" + filename;
                    
                    image = SlideImage.builder()
                        .slideId(slideId)
                        .imageIndex(imageIndex)
                        .originalFilename(filename)
                        .mimeType(match.mimeType)
                        .imageData(imageData)
                        .imageHash(hash)
                        .sizeBytes((long) imageData.length)
                        .publicUrl(publicUrl)
                        .build();
                    
                    image = slideImageRepository.save(image);
                    log.debug("Slide {}: Nueva imagen guardada - {} ({} bytes)", 
                        slideId, filename, imageData.length);
                }
                
                extractedImages.add(image);
                
                // Reemplazar Base64 con URL en el HTML
                cleanedHtml = cleanedHtml.replace(match.fullMatch, image.getPublicUrl());
                
                imageIndex++;
                
            } catch (IllegalArgumentException e) {
                log.warn("Slide {}: Error decodificando Base64: {}", slideId, e.getMessage());
            }
        }
        
        long cleanedSize = cleanedHtml.length();
        long savedBytes = originalSize - cleanedSize;
        
        log.info("Slide {}: Procesamiento completado - {} imágenes, {} -> {} bytes (ahorro: {} bytes)", 
            slideId, extractedImages.size(), originalSize, cleanedSize, savedBytes);
        
        return new ExtractionResult(cleanedHtml, extractedImages, totalFound, originalSize, cleanedSize);
    }
    
    /**
     * Verifica si el contenido HTML tiene imágenes Base64.
     */
    public boolean hasBase64Images(String htmlContent) {
        if (htmlContent == null) return false;
        return BASE64_PATTERN.matcher(htmlContent).find();
    }
    
    /**
     * Cuenta el número de imágenes Base64 en el contenido.
     */
    public int countBase64Images(String htmlContent) {
        if (htmlContent == null) return 0;
        Matcher matcher = BASE64_PATTERN.matcher(htmlContent);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }
    
    /**
     * Calcula el tamaño total de imágenes Base64 en el contenido.
     */
    public long calculateBase64Size(String htmlContent) {
        if (htmlContent == null) return 0;
        Matcher matcher = BASE64_PATTERN.matcher(htmlContent);
        long totalSize = 0;
        while (matcher.find()) {
            String base64Data = matcher.group(2);
            // El tamaño decodificado es aproximadamente 3/4 del tamaño Base64
            totalSize += (base64Data.length() * 3L) / 4L;
        }
        return totalSize;
    }
    
    /**
     * Calcula el hash SHA-256 de los datos de imagen.
     */
    private String calculateHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }
    
    /**
     * Obtiene la extensión de archivo basada en el tipo MIME.
     */
    private String getExtension(String mimeType) {
        return switch (mimeType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            case "image/svg+xml" -> "svg";
            default -> "bin";
        };
    }
    
    /**
     * Registro interno para coincidencias Base64.
     */
    private record Base64Match(String fullMatch, String mimeType, String base64Data) {}
}
