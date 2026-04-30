package com.uisep.slideapi.repository.processed;

import com.uisep.slideapi.entity.processed.SlideProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository para control de sincronización de slides.
 */
@Repository
public interface SlideProcessingStatusRepository extends JpaRepository<SlideProcessingStatus, Integer> {
    
    long countByStatus(SlideProcessingStatus.ProcessingStatus status);
    
    @Query("SELECT s.slideId FROM SlideProcessingStatus s WHERE s.status IN ('PENDING', 'FAILED') ORDER BY s.slideId")
    List<Integer> findPendingSlideIds();
    
    List<SlideProcessingStatus> findByStatus(SlideProcessingStatus.ProcessingStatus status);
    
    @Query("SELECT s.slideId FROM SlideProcessingStatus s WHERE s.status = 'COMPLETED'")
    List<Integer> findCompletedSlideIds();
    
    @Transactional
    @Modifying
    @Query("UPDATE SlideProcessingStatus s SET s.status = :status WHERE s.slideId IN :ids")
    int updateStatusByIds(@Param("status") SlideProcessingStatus.ProcessingStatus status,
                          @Param("ids") List<Integer> ids);
    
    @Query("SELECT s.status, COUNT(s) FROM SlideProcessingStatus s GROUP BY s.status")
    List<Object[]> getProcessingStatistics();
}
