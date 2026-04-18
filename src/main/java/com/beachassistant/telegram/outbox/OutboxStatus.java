package com.beachassistant.telegram.outbox;

public enum OutboxStatus {
    PENDING,
    IN_FLIGHT,
    SENT,
    FAILED
}
