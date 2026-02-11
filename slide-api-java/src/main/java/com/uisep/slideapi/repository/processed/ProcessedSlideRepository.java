package com.uisep.slideapi.repository.processed;

import com.uisep.slideapi.entity.processed.ProcessedSlide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para slides procesados (LECTURA/ESCRITURA).
 */
@Repository
public interface ProcessedSlideRepository extends JpaRepository<ProcessedSlide, Integer> {
    
    // Slides por canal
    List<ProcessedSlide> findByChannelId(Integer channelId);
    
    // Slides activos
    List<ProcessedSlide> findByActiveTrue();
    
    // Slides publicados
    List<ProcessedSlide> findByIsPublishedTrue();
    
    // Slides activos y publicados (para la app)
    List<ProcessedSlide> findByActiveTrueAndIsPublishedTrue();
    
    // Slides por tipo
    List<ProcessedSlide> findBySlideType(String slideType);
    
    // Slides por estado de migración
    List<ProcessedSlide> findByMigrationStatus(String status);
    
    // Slides pendientes de migración
    @Query("SELECT s FROM ProcessedSlide s WHERE s.migrationStatus = 'PENDING' AND s.hasBase64Original = true")
    List<ProcessedSlide> findPendingMigration();
    
    // Slides que necesitan actualización (modificados en origen después de última sincronización)
    @Query("SELECT s FROM ProcessedSlide s WHERE s.odooWriteDate > s.lastSyncedAt")
    List<ProcessedSlide> findNeedingUpdate();
    
    // Buscar por nombre
    @Query("SELECT s FROM ProcessedSlide s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<ProcessedSlide> searchByName(@Param("query") String query);
    
    // Estadísticas de ahorro de espacio
    @Query("""
        SELECT 
            COUNT(s) as total,
            SUM(s.originalSizeBytes) as originalSize,
            SUM(s.processedSizeBytes) as processedSize,
            SUM(s.imagesExtracted) as totalImagesExtracted
        FROM ProcessedSlide s
        WHERE s.migrationStatus = 'COMPLETED'
        """)
    Object[] getMigrationStatistics();
    
    // Slides sin procesar (existen en origen pero no aquí)
    @Query(value = """
        SELECT id FROM slide_slide 
        WHERE active = true 
        AND id NOT IN (SELECT ps.id FROM slides ps)
        """, nativeQuery = true)
    List<Integer> findUnprocessedSlideIds();
    
    // Contar por estado de migración
    @Query("""
        SELECT s.migrationStatus, COUNT(s) 
        FROM ProcessedSlide s 
        GROUP BY s.migrationStatus
        """)
    List<Object[]> countByMigrationStatus();
    
    // Slides procesados después de cierta fecha
    List<ProcessedSlide> findByLastSyncedAtAfter(LocalDateTime date);
    
    // Slides más grandes originalmente
    List<ProcessedSlide> findTop10ByOrderByOriginalSizeBytesDesc();
    
    // Slides con mayor ahorro de espacio
    @Query("""
        SELECT s FROM ProcessedSlide s 
        WHERE s.originalSizeBytes > 0 AND s.processedSizeBytes > 0
        ORDER BY (s.originalSizeBytes - s.processedSizeBytes) DESC
        """)
    List<ProcessedSlide> findTopBySavings();
}
