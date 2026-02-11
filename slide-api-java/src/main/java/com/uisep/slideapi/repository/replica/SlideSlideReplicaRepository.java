package com.uisep.slideapi.repository.replica;

import com.uisep.slideapi.entity.replica.SlideSlideReplica;
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
        WHERE html_content::text LIKE '%data:image%base64%'
        AND active = true
        ORDER BY pg_column_size(html_content) DESC
        """, nativeQuery = true)
    List<SlideSlideReplica> findSlidesWithBase64Images();
    
    // Slides con Base64 por tamaño (paginado)
    @Query(value = """
        SELECT * FROM slide_slide 
        WHERE html_content::text LIKE '%data:image%base64%'
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
    
    // Estadísticas de Base64
    @Query(value = """
        SELECT 
            COUNT(*) as total,
            SUM(pg_column_size(html_content)) as total_size
        FROM slide_slide 
        WHERE html_content::text LIKE '%data:image%base64%'
        AND active = true
        """, nativeQuery = true)
    Object[] getBase64Statistics();
    
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
}
