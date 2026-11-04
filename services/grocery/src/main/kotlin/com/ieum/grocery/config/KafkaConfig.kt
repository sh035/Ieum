package com.ieum.grocery.config

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
	fun familyGroceryDltTopic() = TopicBuilder.name(Topics.FAMILY_GROCERY_DLT).build()

	@Bean
	fun kafkaBackOffScheduler() = ThreadPoolTaskScheduler()

	@Bean
	fun kafkaErrorHandler(
		template: KafkaTemplate<String, String>,
		registry: KafkaListenerEndpointRegistry,
		kafkaBackOffScheduler: ThreadPoolTaskScheduler,
	) = familyEventErrorHandler(template, Topics.FAMILY_GROCERY_DLT, registry, kafkaBackOffScheduler)
}
