package com.ieum.family.repository

import com.ieum.family.domain.entity.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID> {

	// 한 주기가 ShedLock 최대 잠금 1분 안에 끝나도록 10개씩 꺼낸다 (10 x 전송 대기 5초).
	// 같은 트랜잭션에서 쌓인 이벤트는 시각이 같을 수 있어 시간순 UUID 로 순서를 한 번 더 정한다.
	fun findTop10BySentAtIsNullOrderByCreatedAtAscOutboxEventIdAsc(): List<OutboxEvent>

	fun deleteAllBySentAtBefore(before: LocalDateTime)
}
