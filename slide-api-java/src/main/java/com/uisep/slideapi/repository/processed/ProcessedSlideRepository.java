package com.uisep.slideapi.repository.processed;

import com.uisep.slideapi.entity.processed.ProcessedSlide;
import org.springframework.data.domain.Page;
import com.uisep.slideapi.repository.processed.ProcessedSlideProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

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

    // Con paginación (para evitar respuestas enormes)
    Page<ProcessedSlide> findByActiveTrueAndIsPublishedTrue(Pageable pageable);

    // Slides por tipo
    List<ProcessedSlide> findBySlideType(String slideType);

    // Slides por estado de migración
    List<ProcessedSlide> findByMigrationStatus(String status);

    // Slides pendientes de migración
    @Query("SELECT s FROM ProcessedSlide s WHERE s.migrationStatus = 'PENDING' AND s.hasBase64Original = true")
    List<ProcessedSlide> findPendingMigration();

    // Slides que necesitan actualización
    @Query("SELECT s FROM ProcessedSlide s WHERE s.odooWriteDate > s.lastSyncedAt")
    List<ProcessedSlide> findNeedingUpdate();

    // Buscar por nombre
    @Query("SELECT s FROM ProcessedSlide s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<ProcessedSlide> searchByName(@Param("query") String query);

    // Solo IDs (para purge sin cargar htmlContent)
    @Query(value = "SELECT id FROM slide_api.slides", nativeQuery = true)
    List<Integer> findAllIds();

    // Conteo por canal (sin cargar entidades)
    long countByChannelId(Integer channelId);

    // Suma de processedSizeBytes por canal (sin cargar entidades)
    @Query(value = "SELECT COALESCE(SUM(processed_size_bytes), 0) FROM slide_api.slides WHERE channel_id = :channelId", nativeQuery = true)
    Long sumProcessedSizeByChannelId(@Param("channelId") Integer channelId);

    // Estadísticas de migración — consultas individuales (evitar Object[] multi-columna con Hibernate 6)
    long countByMigrationStatus(String migrationStatus);

    @Query("SELECT COALESCE(SUM(s.originalSizeBytes), 0) FROM ProcessedSlide s WHERE s.migrationStatus = 'COMPLETED'")
    Long sumOriginalSizeCompleted();

    @Query("SELECT COALESCE(SUM(s.processedSizeBytes), 0) FROM ProcessedSlide s WHERE s.migrationStatus = 'COMPLETED'")
    Long sumProcessedSizeCompleted();

    @Query("SELECT COALESCE(SUM(s.imagesExtracted), 0) FROM ProcessedSlide s WHERE s.migrationStatus = 'COMPLETED'")
    Long sumImagesExtractedCompleted();

    // Contar por estado de migración (agrupado)
    @Query("""
        SELECT s.migrationStatus, COUNT(s)
        FROM ProcessedSlide s
        GROUP BY s.migrationStatus
        """)
    List<Object[]> groupCountByMigrationStatus();

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

    // Paginated projection queries (avoids loading htmlContent - used by SlideQueryService)
    @Query("SELECT s FROM ProcessedSlide s WHERE s.active = true AND s.isPublished = true ORDER BY s.id ASC")
    Page<ProcessedSlideProjection> findPublishedSlidesProjected(Pageable pageable);

    @Query("SELECT s FROM ProcessedSlide s WHERE s.channelId = :channelId AND s.active = true ORDER BY s.id ASC")
    Page<ProcessedSlideProjection> findByChannelIdProjected(@Param("channelId") Integer channelId, Pageable pageable);

    @Query("SELECT s FROM ProcessedSlide s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')) AND s.active = true ORDER BY s.id ASC")
    Page<ProcessedSlideProjection> searchByNameProjected(@Param("query") String query, Pageable pageable);


    // IDs y fechas de escritura Odoo para slides procesados (detección de cambios)
    @Query("SELECT s.id, s.odooWriteDate FROM ProcessedSlide s WHERE s.id IN :ids")
    List<Object[]> findWriteDatesByIds(@Param("ids") List<Integer> ids);
}
