package com.ieum.family.service

import com.ieum.events.FamilyEventData
import com.ieum.events.IeumEvent
import com.ieum.events.Topics
import com.ieum.family.domain.entity.OutboxEvent
import com.ieum.family.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Service
class OutboxRelayService(
	private val outboxEventRepository: OutboxEventRepository,
	private val kafkaTemplate: KafkaTemplate<String, String>,
	private val objectMapper: ObjectMapper,
	private val clock: Clock,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// 같은 가족의 이벤트 순서가 바뀌지 않도록 실패하면 거기서 멈추고 다음 주기에 그 행부터 다시 보낸다.
	// 보낸 뒤 sent_at 저장 전에 죽으면 다시 보내므로 받는 쪽은 같은 이벤트를 두 번 받아도 결과가 같아야 한다.
	fun relay() {
		for (event in outboxEventRepository.findTop100BySentAtIsNullOrderByCreatedAtAscOutboxEventIdAsc()) {
			try {
				kafkaTemplate.send(Topics.FAMILY, event.familyId.toString(), envelope(event)).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			} catch (e: Exception) {
				log.warn("Family event send failed. outboxEventId={}, type={}", event.outboxEventId, event.eventType, e)
				return
			}
			event.sentAt = LocalDateTime.now(clock)
			outboxEventRepository.save(event)
		}
	}

	@Transactional
	fun cleanup() {
		outboxEventRepository.deleteAllBySentAtBefore(LocalDateTime.now(clock).minus(RETENTION))
	}

	private fun envelope(event: OutboxEvent) = objectMapper.writeValueAsString(
		IeumEvent(
			eventId = requireNotNull(event.outboxEventId),
			eventType = event.eventType.name,
			createdAt = event.createdAt,
			data = objectMapper.readValue(event.payload, FamilyEventData::class.java),
		),
	)

	companion object {
		private const val SEND_TIMEOUT_SECONDS = 5L
		private val RETENTION = Duration.ofDays(7)
	}
}
