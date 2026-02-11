package com.uisep.slideapi.repository.processed;

import com.uisep.slideapi.entity.processed.SlideProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository para control de sincronización de slides.
 */
@Repository
public interface SlideProcessingStatusRepository extends JpaRepository<SlideProcessingStatus, Integer> {
    
    /**
     * Cuenta cuántos slides están en cada estado.
     */
    long countByStatus(SlideProcessingStatus.ProcessingStatus status);
    
    /**
     * Obtiene IDs de slides que aún no han sido procesados o fallaron.
     */
    @Query("SELECT s.slideId FROM SlideProcessingStatus s WHERE s.status IN ('PENDING', 'FAILED') ORDER BY s.slideId")
    List<Integer> findPendingSlideIds();
    
    /**
     * Obtiene slides que están en estado PROCESSING (posiblemente interrumpidos).
     */
    List<SlideProcessingStatus> findByStatus(SlideProcessingStatus.ProcessingStatus status);
    
    /**
     * Obtiene estadísticas de procesamiento.
     */
    @Query("SELECT s.status, COUNT(s) FROM SlideProcessingStatus s GROUP BY s.status")
    List<Object[]> getProcessingStatistics();
}
