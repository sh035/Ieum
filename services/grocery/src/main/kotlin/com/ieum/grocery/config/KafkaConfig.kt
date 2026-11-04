package com.ieum.grocery.config

import com.ieum.events.Topics
import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerPausingBackOffHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.ListenerContainerPauseService
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.util.backoff.BackOff
import org.springframework.util.backoff.BackOffExecution
import tools.jackson.core.JacksonException
import java.time.Duration

@Configuration
class KafkaConfig {

	@Bean
	fun familyGroceryDltTopic() = TopicBuilder.name(Topics.FAMILY_GROCERY_DLT).build()

	@Bean
	fun kafkaBackOffScheduler() = ThreadPoolTaskScheduler()

	// 가족 이벤트를 놓치면 데이터가 어긋나서, DB 가 돌아올 때까지 1분 / 3분 / 10분 뒤에 다시 시도한다.
	// 그래도 실패하거나 다시 해도 소용없는 메시지는 DLT 로 보내고 다음으로 넘어간다.
	// 기다리는 시간이 max.poll.interval(5분) 보다 길어 consumer 가 그룹에서 빠지지 않게, 스레드를 재우지 않고 컨테이너를 멈춘다.
	@Bean
	fun kafkaErrorHandler(
		template: KafkaTemplate<String, String>,
		registry: KafkaListenerEndpointRegistry,
		kafkaBackOffScheduler: ThreadPoolTaskScheduler,
	) = DefaultErrorHandler(
		DeadLetterPublishingRecoverer(template) { _, _ -> TopicPartition(Topics.FAMILY_GROCERY_DLT, -1) },
		FAMILY_EVENT_BACK_OFF,
		ContainerPausingBackOffHandler(ListenerContainerPauseService(registry, kafkaBackOffScheduler)),
	).apply {
		addNotRetryableExceptions(JacksonException::class.java, IllegalArgumentException::class.java)
	}

	companion object {
		private val RETRY_INTERVALS = listOf(Duration.ofMinutes(1), Duration.ofMinutes(3), Duration.ofMinutes(10))

		val FAMILY_EVENT_BACK_OFF = BackOff {
			val intervals = RETRY_INTERVALS.iterator()
			BackOffExecution { if (intervals.hasNext()) intervals.next().toMillis() else BackOffExecution.STOP }
		}
	}
}
