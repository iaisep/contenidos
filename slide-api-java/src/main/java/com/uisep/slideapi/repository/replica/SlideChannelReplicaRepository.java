package com.uisep.slideapi.repository.replica;

import com.uisep.slideapi.entity.replica.SlideChannelReplica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio para consultar canales/cursos desde la réplica de Odoo.
 * SOLO OPERACIONES DE LECTURA.
 */
@Repository
public interface SlideChannelReplicaRepository extends JpaRepository<SlideChannelReplica, Integer> {
    
    // Canales activos
    List<SlideChannelReplica> findByActiveTrue();
    
    // Canales publicados y activos
    List<SlideChannelReplica> findByIsPublishedTrueAndActiveTrue();
    
    // Canales no publicados (posibles candidatos a depuración)
    List<SlideChannelReplica> findByIsPublishedFalseAndActiveTrue();
    
    // Estadísticas de canales con sus slides
    @Query(value = """
        SELECT 
            c.id,
            c.name,
            c.active,
            c.is_published,
            COUNT(s.id) as slide_count,
            COALESCE(SUM(pg_column_size(s.html_content)), 0) as total_size
        FROM slide_channel c
        LEFT JOIN slide_slide s ON s.channel_id = c.id
        WHERE c.active = true
        GROUP BY c.id, c.name, c.active, c.is_published
        ORDER BY total_size DESC
        """, nativeQuery = true)
    List<Object[]> getChannelStatistics();
    
    // Canales no publicados con métricas de slides
    @Query(value = """
        SELECT 
            c.id,
            c.name,
            COUNT(s.id) as slides,
            pg_size_pretty(COALESCE(SUM(pg_column_size(s.html_content))::bigint, 0)) as size,
            COALESCE(SUM(s.total_views), 0) as views
        FROM slide_channel c
        JOIN slide_slide s ON s.channel_id = c.id
        WHERE c.is_published = false AND c.active = true
        GROUP BY c.id, c.name
        ORDER BY SUM(pg_column_size(s.html_content)) DESC NULLS LAST
        """, nativeQuery = true)
    List<Object[]> getUnpublishedChannelsWithMetrics();
}
