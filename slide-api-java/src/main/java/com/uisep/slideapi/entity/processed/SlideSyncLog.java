package com.uisep.slideapi.entity.processed;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "slide_sync_log", indexes = {
    @Index(name = "idx_sync_log_run_id", columnList = "sync_run_id"),
    @Index(name = "idx_sync_log_slide_id", columnList = "slide_id"),
    @Index(name = "idx_sync_log_synced_at", columnList = "synced_at"),
    @Index(name = "idx_sync_log_action", columnList = "action")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlideSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sync_run_id", length = 36, nullable = false)
    private String syncRunId;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @Column(name = "slide_id", nullable = false)
    private Integer slideId;

    @Column(name = "slide_name")
    private String slideName;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20, nullable = false)
    private SyncAction action;

    @Column(name = "slide_type", length = 50)
    private String slideType;

    @Column(name = "channel_id")
    private Integer channelId;

    @Column(name = "channel_name")
    private String channelName;

    @Column(name = "odoo_write_date")
    private LocalDateTime odooWriteDate;

    @Column(name = "original_size_bytes")
    private Long originalSizeBytes;

    @Column(name = "processed_size_bytes")
    private Long processedSizeBytes;

    @Column(name = "images_extracted")
    private Integer imagesExtracted;

    @Column(name = "message", length = 500)
    private String message;

    public enum SyncAction {
        CREATED, UPDATED, FAILED
    }
}
