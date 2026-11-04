package com.ieum.planner

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
		fun plannerProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.kafka.bootstrap-servers") { "localhost:9092" }
			registry.add("spring.kafka.admin.auto-create") { "false" }
			// 테스트 중 실제 시계로 스케줄이 돌면 락을 먼저 잡아 테스트 호출이 건너뛰어진다.
			registry.add("planner.scheduler.due-soon-cron") { "-" }
		}
	}
}
