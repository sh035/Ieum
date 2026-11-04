package com.ieum.planner.config

import com.ieum.events.Topics
import com.ieum.events.familyEventErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.TaskScheduler

@Configuration
class KafkaConfig {

	@Bean
	fun familyPlannerDltTopic() = TopicBuilder.name(Topics.FAMILY_PLANNER_DLT).build()

	// 재시도 대기 후 컨테이너를 다시 켜는 일은 스케줄 작업용 TaskScheduler 를 같이 쓴다.
	// 전용 TaskScheduler 빈을 두면 Boot 기본 스케줄러가 빠지고 @Scheduled 도 그 빈을 쓰게 되어 어차피 공유된다.
	@Bean
	fun kafkaErrorHandler(
		template: KafkaTemplate<String, String>,
		registry: KafkaListenerEndpointRegistry,
		taskScheduler: TaskScheduler,
	) = familyEventErrorHandler(template, Topics.FAMILY_PLANNER_DLT, registry, taskScheduler)
}
