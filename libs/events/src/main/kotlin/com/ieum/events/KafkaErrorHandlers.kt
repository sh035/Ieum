package com.ieum.events

import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.BackOffHandler
import org.springframework.kafka.listener.ContainerPausingBackOffHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultBackOffHandler
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.ListenerContainerPauseService
import org.springframework.kafka.listener.ListenerContainerRegistry
import org.springframework.scheduling.TaskScheduler
import org.springframework.util.backoff.BackOff
import org.springframework.util.backoff.BackOffExecution
import tools.jackson.core.JacksonException
import java.time.Duration

private val FAMILY_EVENT_RETRY_INTERVALS = listOf(Duration.ofMinutes(1), Duration.ofMinutes(3), Duration.ofMinutes(10))

// 가족 이벤트를 놓치면 데이터가 어긋나서, DB 가 돌아올 때까지 1분 / 3분 / 10분 뒤에 다시 시도한다.
val FAMILY_EVENT_BACK_OFF = BackOff {
	val intervals = FAMILY_EVENT_RETRY_INTERVALS.iterator()
	BackOffExecution { if (intervals.hasNext()) intervals.next().toMillis() else BackOffExecution.STOP }
}

// 일시 장애는 backOff 만큼 다시 시도하고, 그래도 실패하거나 다시 해도 소용없는 메시지는 DLT 로 보내고 다음으로 넘어간다.
fun deadLetterErrorHandler(
	template: KafkaTemplate<String, String>,
	dltTopic: String,
	backOff: BackOff,
	backOffHandler: BackOffHandler = DefaultBackOffHandler(),
) = DefaultErrorHandler(
	DeadLetterPublishingRecoverer(template) { _, _ -> TopicPartition(dltTopic, -1) },
	backOff,
	backOffHandler,
).apply {
	addNotRetryableExceptions(JacksonException::class.java, IllegalArgumentException::class.java)
}

// 기다리는 시간이 max.poll.interval(5분) 보다 길어 consumer 가 그룹에서 빠지지 않게, 스레드를 재우지 않고 컨테이너를 멈춘다.
fun familyEventErrorHandler(
	template: KafkaTemplate<String, String>,
	dltTopic: String,
	registry: ListenerContainerRegistry,
	scheduler: TaskScheduler,
) = deadLetterErrorHandler(
	template,
	dltTopic,
	FAMILY_EVENT_BACK_OFF,
	ContainerPausingBackOffHandler(ListenerContainerPauseService(registry, scheduler)),
)
