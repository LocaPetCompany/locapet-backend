package com.vivire.locapet.domain.outbox

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 서비스 레이어에서 도메인 이벤트를 outbox 에 기록하는 유틸.
 * 반드시 서비스 트랜잭션 내에서 호출 — AFTER_COMMIT 시점에 퍼블리셔가 PENDING 이벤트를 소비한다.
 *
 * 로드맵: 16-implementation-roadmap.md PR-01
 * 컨벤션: 13.5 이벤트 Dedupe Key
 */
@Component
class OutboxWriter(
    private val repository: OutboxEventRepository,
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun write(
        aggregateType: String,
        aggregateId: Long,
        eventType: String,
        payload: Map<String, Any?>,
        dedupeKey: String? = null,
    ): OutboxEvent {
        if (dedupeKey != null) {
            repository.findByDedupeKey(dedupeKey)?.let { return it }
        }
        return try {
            repository.save(
                OutboxEvent(
                    aggregateType = aggregateType,
                    aggregateId = aggregateId,
                    eventType = eventType,
                    payload = payload,
                    dedupeKey = dedupeKey,
                )
            )
        } catch (ex: DataIntegrityViolationException) {
            // dedupe_key UNIQUE 부분 인덱스 경합 시 기존 이벤트 반환
            if (dedupeKey != null) {
                repository.findByDedupeKey(dedupeKey)
                    ?: throw ex
            } else {
                throw ex
            }
        }
    }
}
