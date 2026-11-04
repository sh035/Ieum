package com.ieum.expense.config

import com.ieum.events.Topics
import com.ieum.events.familyEventErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class KafkaConfig {

	@Bean
	fun familyExpenseDltTopic() = TopicBuilder.name(Topics.FAMILY_EXPENSE_DLT).build()

	@Bean
	fun kafkaBackOffScheduler() = ThreadPoolTaskScheduler()

	@Bean
	fun kafkaErrorHandler(
		template: KafkaTemplate<String, String>,
		registry: KafkaListenerEndpointRegistry,
		kafkaBackOffScheduler: ThreadPoolTaskScheduler,
	) = familyEventErrorHandler(template, Topics.FAMILY_EXPENSE_DLT, registry, kafkaBackOffScheduler)
}
