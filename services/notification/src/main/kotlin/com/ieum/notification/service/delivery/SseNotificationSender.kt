package com.ieum.notification.service.delivery

import com.ieum.events.Topics
import com.ieum.notification.controller.dto.response.NotificationResponse
import com.ieum.notification.domain.entity.Notification
import org.springframework.core.annotation.Order
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

// 받는 사람이 어느 Pod 에 붙어 있는지 모르므로 모든 Pod 에 뿌린다. 앱을 켜 둔 사용자가 FCM 왕복을 기다리지 않게 먼저 보낸다.
@Order(0)
@Component
class SseNotificationSender(
	private val kafkaTemplate: KafkaTemplate<String, String>,
	private val objectMapper: ObjectMapper,
) : NotificationSender {

	override fun send(notification: Notification) {
		val delivery = NotificationDelivery(notification.accountId, NotificationResponse.from(notification))
		kafkaTemplate.send(Topics.NOTIFICATION_DELIVERY, notification.accountId.toString(), objectMapper.writeValueAsString(delivery))
	}
}
