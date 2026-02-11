package com.uisep.slideapi.repository.processed;

import com.uisep.slideapi.entity.processed.ProcessedChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio para canales procesados.
 */
@Repository
public interface ProcessedChannelRepository extends JpaRepository<ProcessedChannel, Integer> {
    
    // Canales activos
    List<ProcessedChannel> findByActiveTrue();
    
    // Canales publicados
    List<ProcessedChannel> findByIsPublishedTrue();
    
    // Canales activos y publicados (para la app)
    List<ProcessedChannel> findByActiveTrueAndIsPublishedTrue();
    
    // Estadísticas de canales
    @Query("""
        SELECT 
            COUNT(c) as totalChannels,
            SUM(c.slideCount) as totalSlides,
            SUM(c.totalSizeBytes) as totalSize
        FROM ProcessedChannel c
        WHERE c.active = true
        """)
    Object[] getChannelStatistics();
    
    // Canales ordenados por número de slides
    List<ProcessedChannel> findByActiveTrueOrderBySlideCountDesc();
}
