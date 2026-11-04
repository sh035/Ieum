package com.ieum.notification.config

import com.ieum.events.Topics
import com.ieum.events.deadLetterErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.util.backoff.FixedBackOff

@Configuration
class KafkaConfig {

	@Bean
	fun notificationTopic() = TopicBuilder.name(Topics.NOTIFICATION).build()

	@Bean
	fun notificationDeliveryTopic() = TopicBuilder.name(Topics.NOTIFICATION_DELIVERY).build()

	@Bean
	fun notificationDltTopic() = TopicBuilder.name(Topics.NOTIFICATION_DLT).build()

	// 일시 장애(gRPC 등)는 1초 간격으로 두 번 더 시도한다.
	@Bean
	fun kafkaErrorHandler(template: KafkaTemplate<String, String>) =
		deadLetterErrorHandler(template, Topics.NOTIFICATION_DLT, FixedBackOff(1000, 2))
}
