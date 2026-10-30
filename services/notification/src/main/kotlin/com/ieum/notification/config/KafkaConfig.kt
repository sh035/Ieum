package com.ieum.notification.config

import com.ieum.events.Topics
import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff
import tools.jackson.core.JacksonException

@Configuration
class KafkaConfig {

	@Bean
	fun notificationTopic() = TopicBuilder.name(Topics.NOTIFICATION).build()

	@Bean
	fun notificationDeliveryTopic() = TopicBuilder.name(Topics.NOTIFICATION_DELIVERY).build()

	@Bean
	fun notificationDltTopic() = TopicBuilder.name(Topics.NOTIFICATION_DLT).build()

	// 일시 장애(gRPC 등)는 두 번 더 시도하고, 그래도 실패하거나 다시 해도 소용없는 메시지는 DLT 로 보내고 다음으로 넘어간다.
	@Bean
	fun kafkaErrorHandler(template: KafkaTemplate<Any, Any>) =
		DefaultErrorHandler(
			DeadLetterPublishingRecoverer(template) { _, _ -> TopicPartition(Topics.NOTIFICATION_DLT, -1) },
			FixedBackOff(1000, 2),
		).apply {
			addNotRetryableExceptions(JacksonException::class.java, IllegalArgumentException::class.java)
		}
}
