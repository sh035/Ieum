package com.ieum.grocery.config

import com.ieum.events.Topics
import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOff
import tools.jackson.core.JacksonException

@Configuration
class KafkaConfig {

	@Bean
	fun familyGroceryDltTopic() = TopicBuilder.name(Topics.FAMILY_GROCERY_DLT).build()

	// 가족 이벤트를 놓치면 데이터가 어긋나서, DB 가 돌아올 때까지 1초부터 최대 1분 간격으로 30분 동안 다시 시도한다.
	// 그래도 실패하거나 다시 해도 소용없는 메시지는 DLT 로 보내고 다음으로 넘어간다.
	@Bean
	fun kafkaErrorHandler(template: KafkaTemplate<String, String>) =
		DefaultErrorHandler(
			DeadLetterPublishingRecoverer(template) { _, _ -> TopicPartition(Topics.FAMILY_GROCERY_DLT, -1) },
			ExponentialBackOff(1000, 2.0).apply {
				maxInterval = 60_000
				maxElapsedTime = 30 * 60_000L
			},
		).apply {
			addNotRetryableExceptions(JacksonException::class.java, IllegalArgumentException::class.java)
		}
}
