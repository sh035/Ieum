package com.ieum.events

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.UUID

// 알림 실패가 이미 커밋된 저장이나 API 응답을 깨면 안 되므로 예외는 로그만 남긴다.
@Component
class NotificationEventRelay(
	private val kafkaTemplate: KafkaTemplate<String, String>,
	private val objectMapper: ObjectMapper,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	fun relay(event: NotificationEvent) {
		try {
			val message = objectMapper.writeValueAsString(IeumEvent(UUID.randomUUID(), event.type.name, LocalDateTime.now(), event.data))
			// 순서 보장이 필요 없어 키는 파티션 분산용이다. 대상이 없는 알림은 첫 받는 사람으로 대신한다.
			val key = (event.data.targetId ?: event.data.recipientAccountIds.first()).toString()
			kafkaTemplate.send(Topics.NOTIFICATION, key, message)
				?.whenComplete { _, e -> e?.let { log.warn("Notification event send failed. type={}", event.type, it) } }
		} catch (e: Exception) {
			log.warn("Notification event send failed. type={}", event.type, e)
		}
	}
}
