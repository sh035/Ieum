package com.ieum.family.repository

import com.ieum.family.domain.entity.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID> {

	// 같은 트랜잭션에서 쌓인 이벤트는 시각이 같을 수 있어 시간순 UUID 로 순서를 한 번 더 정한다.
	fun findTop100BySentAtIsNullOrderByCreatedAtAscOutboxEventIdAsc(): List<OutboxEvent>

	fun deleteAllBySentAtBefore(before: LocalDateTime)
}
