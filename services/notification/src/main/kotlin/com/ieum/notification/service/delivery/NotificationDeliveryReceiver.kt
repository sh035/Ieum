package com.ieum.notification.service.delivery

import com.ieum.events.Topics
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

// Pod 마다 다른 consumer group 이라 모든 Pod 가 모든 메시지를 받는다. 지난 메시지는 알림함으로 메우므로 최신부터 읽는다.
@Component
class NotificationDeliveryReceiver(
	private val objectMapper: ObjectMapper,
	private val sseEmitterRegistry: SseEmitterRegistry,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	@KafkaListener(
		id = "sse-delivery",
		topics = [Topics.NOTIFICATION_DELIVERY],
		groupId = "notification-sse-#{T(java.util.UUID).randomUUID()}",
		properties = ["auto.offset.reset=\${notification.delivery.auto-offset-reset}"],
	)
	fun receive(message: String) {
		// 실시간 표시용 복사본이라 다시 처리할 가치가 없다. DLT 로 보내면 Pod 수만큼 쌓인다.
		val delivery = try {
			objectMapper.readValue<NotificationDelivery>(message)
		} catch (e: JacksonException) {
			log.warn("Broken notification delivery dropped. message={}", e.originalMessage)
			return
		}
		sseEmitterRegistry.send(delivery.accountId, EVENT_NAME, delivery.notification)
	}

	companion object {
		private const val EVENT_NAME = "notification"
	}
}
