package com.uisep.slideapi.entity.processed;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "excluded_slides", schema = "slide_api")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcludedSlide {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "channel_id")
    private Integer channelId;

    @Column(name = "create_date")
    private LocalDateTime createDate;

    @Column(name = "reason", length = 30)
    private String reason;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
}
