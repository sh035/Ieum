package com.ieum.family.service

import com.ieum.events.FamilyEventData
import com.ieum.events.FamilyEventType
import com.ieum.family.domain.entity.OutboxEvent
import com.ieum.family.repository.OutboxEventRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.LocalDateTime

// 다른 서비스의 데이터 정리가 빠지면 안 되므로 상태 변경과 같은 트랜잭션에 outbox 로 남기고, OutboxRelayService 가 보낸다.
@Component
class FamilyEventPublisher(
	private val outboxEventRepository: OutboxEventRepository,
	private val objectMapper: ObjectMapper,
	private val clock: Clock,
) {

	fun publish(type: FamilyEventType, data: FamilyEventData) {
		check(TransactionSynchronizationManager.isActualTransactionActive()) {
			"Family event must be published inside a transaction. type=$type"
		}

		outboxEventRepository.save(
			OutboxEvent(
				eventType = type,
				familyId = data.familyId,
				payload = objectMapper.writeValueAsString(data),
				createdAt = LocalDateTime.now(clock),
			),
		)
	}
}
