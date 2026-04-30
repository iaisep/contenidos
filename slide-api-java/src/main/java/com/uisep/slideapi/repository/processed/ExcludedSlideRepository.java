package com.uisep.slideapi.repository.processed;

import com.uisep.slideapi.entity.processed.ExcludedSlide;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExcludedSlideRepository extends JpaRepository<ExcludedSlide, Integer> {

    Page<ExcludedSlide> findByOrderByCreateDateDesc(Pageable pageable);

    Page<ExcludedSlide> findByReasonOrderByCreateDateDesc(String reason, Pageable pageable);

    Page<ExcludedSlide> findByChannelIdOrderByCreateDateDesc(Integer channelId, Pageable pageable);

    Page<ExcludedSlide> findByChannelIdAndReasonOrderByCreateDateDesc(Integer channelId, String reason, Pageable pageable);

    long countByReason(String reason);
}
