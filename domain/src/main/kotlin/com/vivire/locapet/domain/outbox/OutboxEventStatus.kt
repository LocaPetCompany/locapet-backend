package com.vivire.locapet.domain.outbox

enum class OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED,
}
