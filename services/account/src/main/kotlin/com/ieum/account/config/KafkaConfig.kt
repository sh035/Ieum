package com.ieum.account.config

import com.ieum.events.Topics
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaConfig {

	@Bean
	fun accountTopic() = TopicBuilder.name(Topics.ACCOUNT).build()
}
