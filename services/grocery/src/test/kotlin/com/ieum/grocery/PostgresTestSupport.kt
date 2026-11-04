package com.ieum.grocery

import com.ieum.test.IeumTestSupport
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

abstract class PostgresTestSupport : IeumTestSupport() {

	companion object {
		@JvmStatic
		@DynamicPropertySource
		fun groceryProperties(registry: DynamicPropertyRegistry) {
			// 이벤트 처리는 리스너 메서드를 직접 불러 확인하고 Kafka 에는 붙지 않는다.
			registry.add("spring.kafka.bootstrap-servers") { "localhost:9092" }
			registry.add("spring.kafka.admin.auto-create") { "false" }
			registry.add("spring.kafka.listener.auto-startup") { "false" }
		}
	}
}
