package com.ieum.notification.service

import com.ieum.events.IeumEvent
import com.ieum.events.NotificationEventData
import com.ieum.events.Topics
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@Component
class NotificationReceiver(
	private val objectMapper: ObjectMapper,
	private val notificationService: NotificationService,
) {

	@KafkaListener(topics = [Topics.NOTIFICATION])
	fun receive(message: String) {
		notificationService.receive(objectMapper.readValue<IeumEvent<NotificationEventData>>(message))
	}
}
