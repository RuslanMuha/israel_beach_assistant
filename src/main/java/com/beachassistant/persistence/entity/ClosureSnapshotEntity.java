package com.beachassistant.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

/**
 * Latest known closure state for a beach. Multiple rows may exist per beach (history);
 * the most recent by {@code capturedAt} is the effective record.
 */
@Entity
@Table(name = "closure_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class ClosureSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "beach_id", nullable = false)
    private Long beachId;

    @Column(nullable = false)
    private boolean closed;

    @Column(length = 255)
    private String reason;

    /** One of: FEED, ADMIN_OVERRIDE, STUB. */
    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "effective_from", nullable = false)
    private ZonedDateTime effectiveFrom;

    @Column(name = "effective_until")
    private ZonedDateTime effectiveUntil;

    @Column(name = "raw_payload_json", columnDefinition = "TEXT")
    private String rawPayloadJson;

    @Column(name = "captured_at", nullable = false)
    private ZonedDateTime capturedAt;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;
}
