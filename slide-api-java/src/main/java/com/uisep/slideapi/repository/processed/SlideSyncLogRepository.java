package com.uisep.slideapi.repository.processed;

import com.uisep.slideapi.entity.processed.SlideSyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SlideSyncLogRepository extends JpaRepository<SlideSyncLog, Long> {

    Page<SlideSyncLog> findByOrderBySyncedAtDesc(Pageable pageable);

    Page<SlideSyncLog> findByActionOrderBySyncedAtDesc(SlideSyncLog.SyncAction action, Pageable pageable);

    Page<SlideSyncLog> findBySyncRunIdOrderBySyncedAtDesc(String syncRunId, Pageable pageable);

    Page<SlideSyncLog> findBySlideIdOrderBySyncedAtDesc(Integer slideId, Pageable pageable);

    Page<SlideSyncLog> findBySyncedAtBetweenOrderBySyncedAtDesc(
        LocalDateTime from, LocalDateTime to, Pageable pageable);

    // Resumen de ejecuciones de sync (una fila por syncRunId)
    @Query(value = """
        SELECT sync_run_id,
               MIN(synced_at)                                           AS started_at,
               MAX(synced_at)                                           AS last_event_at,
               COUNT(*)                                                 AS total_events,
               SUM(CASE WHEN action = 'CREATED' THEN 1 ELSE 0 END)    AS created,
               SUM(CASE WHEN action = 'UPDATED' THEN 1 ELSE 0 END)    AS updated,
               SUM(CASE WHEN action = 'FAILED'  THEN 1 ELSE 0 END)    AS failed
        FROM public.slide_sync_log
        GROUP BY sync_run_id
        ORDER BY MIN(synced_at) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findSyncRunSummaries(@Param("limit") int limit);

    // Total de ejecuciones distintas (para paginación del listado de runs)
    @Query(value = "SELECT COUNT(DISTINCT sync_run_id) FROM public.slide_sync_log", nativeQuery = true)
    long countDistinctRuns();

    // Cambios recientes: CREATED y UPDATED en rango de fechas
    @Query(value = """
        SELECT * FROM public.slide_sync_log
        WHERE action IN ('CREATED', 'UPDATED')
          AND synced_at BETWEEN :from AND :to
        ORDER BY synced_at DESC
        """, nativeQuery = true,
        countQuery = """
        SELECT COUNT(*) FROM public.slide_sync_log
        WHERE action IN ('CREATED', 'UPDATED')
          AND synced_at BETWEEN :from AND :to
        """)
    Page<SlideSyncLog> findChanges(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable);

    // Conteos rápidos
    long countByAction(SlideSyncLog.SyncAction action);

    @Query("SELECT COUNT(s) FROM SlideSyncLog s WHERE s.syncedAt >= :since")
    long countSince(@Param("since") LocalDateTime since);
}
