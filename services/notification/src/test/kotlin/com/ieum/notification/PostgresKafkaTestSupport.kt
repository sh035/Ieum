package com.ieum.notification

import com.ieum.test.IeumTestSupport
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.kafka.KafkaContainer

abstract class PostgresKafkaTestSupport : IeumTestSupport() {

	companion object {
		@ServiceConnection
		@JvmStatic
		val kafka = KafkaContainer("apache/kafka:4.1.1").apply { start() }

		@JvmStatic
		@DynamicPropertySource
		fun notificationProperties(registry: DynamicPropertyRegistry) {
			// 테스트 중 실제 시계로 스케줄이 돌면 락을 먼저 잡아 테스트 호출이 건너뛰어진다.
			registry.add("notification.scheduler.briefing-cron") { "-" }
			// 테스트는 .env 를 읽지 않는다. 채널은 처음 호출할 때 연결하므로 주소만 채운다.
			registry.add("spring.grpc.client.channel.planner.target") { "localhost:9083" }
			registry.add("spring.grpc.client.channel.grocery.target") { "localhost:9084" }
		}
	}
}
