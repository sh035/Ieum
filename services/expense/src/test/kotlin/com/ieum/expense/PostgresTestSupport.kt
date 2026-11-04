package com.ieum.expense

import com.ieum.events.NotificationEventRelay
import com.ieum.test.IeumTestSupport
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean

abstract class PostgresTestSupport : IeumTestSupport() {

	// 이벤트는 @RecordApplicationEvents 로 확인하고 Kafka 로는 보내지 않는다.
	@MockitoBean
	lateinit var notificationEventRelay: NotificationEventRelay

	companion object {
		@JvmStatic
		@DynamicPropertySource
		fun expenseProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.kafka.bootstrap-servers") { "localhost:9092" }
			registry.add("spring.kafka.admin.auto-create") { "false" }
			// 이벤트 처리는 리스너 메서드를 직접 불러 확인하고 Kafka 에는 붙지 않는다.
			registry.add("spring.kafka.listener.auto-startup") { "false" }
		}
	}
}
