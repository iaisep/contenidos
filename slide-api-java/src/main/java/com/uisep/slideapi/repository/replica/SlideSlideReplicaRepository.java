package com.uisep.slideapi.repository.replica;

import com.uisep.slideapi.entity.replica.SlideSlideReplica;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio para consultar slides desde la réplica de Odoo.
 * SOLO OPERACIONES DE LECTURA.
 */
@Repository
public interface SlideSlideReplicaRepository extends JpaRepository<SlideSlideReplica, Integer> {

    // Slides activos
    List<SlideSlideReplica> findByActiveTrue();

    // Slides activos con paginación (IMPORTANTE para evitar OOM)
    Page<SlideSlideReplica> findByActiveTrue(Pageable pageable);

    // Conteos ligeros (evitar findByActiveTrue().size() que carga entidades completas)
    long countByActiveTrue();
    long countByActiveFalse();

    // IDs de slides relevantes: activos, publicados, en canal activo+publicado
    @Query(value = """
        SELECT s.id FROM slide_slide s
        WHERE s.active = true
          AND s.is_published = true
          AND EXISTS (
              SELECT 1 FROM slide_channel sc
              WHERE sc.id = s.channel_id
                AND sc.active = true
                AND sc.is_published = true
          )
        ORDER BY s.id
        """, nativeQuery = true)
    List<Integer> findActiveSlideIds();

    // Tamaño total de htmlContent en slides inactivos (sin cargar los registros)
    @Query(value = "SELECT COALESCE(SUM(pg_column_size(html_content)), 0) FROM slide_slide WHERE active = false", nativeQuery = true)
    Long sumHtmlContentSizeInactive();

    // Estadísticas de Base64 — consultas separadas (evitar Object[] multi-columna con Hibernate 6)
    @Query(value = "SELECT COUNT(*) FROM slide_slide WHERE CAST(html_content AS text) LIKE '%data:image%base64%' AND active = true", nativeQuery = true)
    Long countSlidesWithBase64();

    @Query(value = "SELECT COALESCE(SUM(pg_column_size(html_content)), 0) FROM slide_slide WHERE CAST(html_content AS text) LIKE '%data:image%base64%' AND active = true", nativeQuery = true)
    Long sumBase64Size();

    // Slides inactivos (candidatos a depuración)
    List<SlideSlideReplica> findByActiveFalse();

    // Slides por canal
    List<SlideSlideReplica> findByChannelId(Integer channelId);

    // Slides por tipo
    List<SlideSlideReplica> findBySlideType(String slideType);

    // Slides publicados y activos
    List<SlideSlideReplica> findByIsPublishedTrueAndActiveTrue();

    // Slides con contenido Base64 (candidatos a migración)
    @Query(value = """
        SELECT * FROM slide_slide
        WHERE CAST(html_content AS text) LIKE '%data:image%base64%'
        AND active = true
        ORDER BY pg_column_size(html_content) DESC
        """, nativeQuery = true)
    List<SlideSlideReplica> findSlidesWithBase64Images();

    // Slides con Base64 por tamaño (paginado)
    @Query(value = """
        SELECT * FROM slide_slide
        WHERE CAST(html_content AS text) LIKE '%data:image%base64%'
        AND active = true
        ORDER BY pg_column_size(html_content) DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<SlideSlideReplica> findSlidesWithBase64ImagesPaginated(
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    // Top N slides más pesados
    @Query(value = """
        SELECT * FROM slide_slide
        WHERE html_content IS NOT NULL
        ORDER BY pg_column_size(html_content) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<SlideSlideReplica> findTopHeaviestSlides(@Param("limit") int limit);

    // Slides modificados después de cierta fecha
    List<SlideSlideReplica> findByWriteDateAfter(LocalDateTime date);

    // Slides modificados después de cierta fecha y activos
    List<SlideSlideReplica> findByWriteDateAfterAndActiveTrue(LocalDateTime date);

    // Contar slides por tipo
    @Query(value = """
        SELECT slide_type, COUNT(*) as count
        FROM slide_slide
        WHERE active = true
        GROUP BY slide_type
        ORDER BY count DESC
        """, nativeQuery = true)
    List<Object[]> countBySlideType();

    // Contar slides por año
    @Query(value = """
        SELECT
            EXTRACT(YEAR FROM create_date) as year,
            COUNT(*) as total,
            SUM(CASE WHEN active THEN 1 ELSE 0 END) as active_count
        FROM slide_slide
        GROUP BY EXTRACT(YEAR FROM create_date)
        ORDER BY year
        """, nativeQuery = true)
    List<Object[]> countByYear();

    // Verificar estado de replicación
    @Query(value = """
        SELECT
            pg_is_in_recovery() as is_replica,
            pg_last_wal_receive_lsn() as last_wal,
            pg_last_xact_replay_timestamp() as last_replay,
            now() - pg_last_xact_replay_timestamp() as lag
        """, nativeQuery = true)
    Object[] getReplicationStatus();

    // IDs y write_date de la réplica para comparar con BD procesada
    @Query(value = "SELECT s.id, s.write_date FROM slide_slide s WHERE s.id IN :ids", nativeQuery = true)
    List<Object[]> findWriteDatesByIds(@Param("ids") List<Integer> ids);
}
