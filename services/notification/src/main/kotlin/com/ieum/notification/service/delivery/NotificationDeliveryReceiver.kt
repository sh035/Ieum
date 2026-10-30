package com.ieum.notification.service.delivery

import com.ieum.events.Topics
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

// Pod 마다 다른 consumer group 이라 모든 Pod 가 모든 메시지를 받는다. 지난 메시지는 알림함으로 메우므로 최신부터 읽는다.
@Component
class NotificationDeliveryReceiver(
	private val objectMapper: ObjectMapper,
	private val sseEmitterRegistry: SseEmitterRegistry,
) {

	@KafkaListener(
		id = "sse-delivery",
		topics = [Topics.NOTIFICATION_DELIVERY],
		groupId = "notification-sse-#{T(java.util.UUID).randomUUID()}",
		properties = ["auto.offset.reset=latest"],
	)
	fun receive(message: String) {
		val delivery = objectMapper.readValue<NotificationDelivery>(message)
		sseEmitterRegistry.send(delivery.accountId, EVENT_NAME, delivery.notification)
	}

	companion object {
		private const val EVENT_NAME = "notification"
	}
}
