package com.ieum.family

import com.ieum.events.NotificationEventRelay
import com.ieum.grpc.client.account.AccountClient
import com.ieum.test.IeumTestSupport
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean

abstract class PostgresTestSupport : IeumTestSupport() {

	// 이벤트는 @RecordApplicationEvents 로 확인하고 Kafka 로는 보내지 않는다.
	@MockitoBean
	lateinit var notificationEventRelay: NotificationEventRelay

	// 초대 대상 조회는 account 를 부르므로 테스트마다 필요한 응답만 정한다.
	@MockitoBean
	lateinit var accountClient: AccountClient

	companion object {
		@JvmStatic
		@DynamicPropertySource
		fun familyProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.kafka.bootstrap-servers") { "localhost:9092" }
			registry.add("spring.kafka.admin.auto-create") { "false" }
			registry.add("spring.grpc.client.channel.account.target") { "localhost:9081" }
			registry.add("family.scheduler.outbox-relay-cron") { "-" }
			registry.add("family.scheduler.outbox-cleanup-cron") { "-" }
		}
	}
}
