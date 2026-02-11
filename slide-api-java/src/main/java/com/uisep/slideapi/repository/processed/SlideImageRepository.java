package com.uisep.slideapi.repository.processed;

import com.uisep.slideapi.entity.processed.SlideImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para imágenes extraídas de slides.
 */
@Repository
public interface SlideImageRepository extends JpaRepository<SlideImage, Long> {
    
    // Imágenes de un slide
    List<SlideImage> findBySlideIdOrderByImageIndex(Integer slideId);
    
    // Buscar por hash (para deduplicación)
    Optional<SlideImage> findByImageHash(String imageHash);
    
    // Verificar si existe por hash
    boolean existsByImageHash(String imageHash);
    
    // Imágenes por tipo MIME
    List<SlideImage> findByMimeType(String mimeType);
    
    // Contar imágenes por slide
    @Query("SELECT COUNT(i) FROM SlideImage i WHERE i.slideId = :slideId")
    long countBySlideId(@Param("slideId") Integer slideId);
    
    // Tamaño total de imágenes por slide
    @Query("SELECT COALESCE(SUM(i.sizeBytes), 0) FROM SlideImage i WHERE i.slideId = :slideId")
    long getTotalSizeBySlideId(@Param("slideId") Integer slideId);
    
    // Estadísticas generales
    @Query("""
        SELECT 
            COUNT(i) as totalImages,
            COALESCE(SUM(i.sizeBytes), 0) as totalSize,
            COALESCE(AVG(i.sizeBytes), 0) as avgSize
        FROM SlideImage i
        """)
    Object[] getImageStatistics();
    
    // Imágenes más grandes
    List<SlideImage> findTop10ByOrderBySizeBytesDesc();
    
    // Contar por tipo MIME
    @Query("""
        SELECT i.mimeType, COUNT(i), SUM(i.sizeBytes) 
        FROM SlideImage i 
        GROUP BY i.mimeType
        """)
    List<Object[]> countByMimeType();
}
