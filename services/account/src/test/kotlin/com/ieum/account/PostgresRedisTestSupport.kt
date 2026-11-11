package com.ieum.account

import com.ieum.grpc.client.family.FamilyClient
import com.ieum.test.IeumTestSupport
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.GenericContainer
import java.util.Base64

abstract class PostgresRedisTestSupport : IeumTestSupport() {

	// 탈퇴는 family 를 부르므로 테스트마다 필요한 응답만 정한다.
	@MockitoBean
	lateinit var familyClient: FamilyClient

	// 탈퇴 이벤트는 Kafka 로 보내지 않고 보낸 내용만 확인한다.
	@MockitoBean
	lateinit var kafkaTemplate: KafkaTemplate<String, String>

	companion object {
		@ServiceConnection
		@JvmStatic
		val redis = GenericContainer("redis:8")
			.withExposedPorts(6379)
			.apply { start() }

		// account 는 토큰을 직접 발급하므로 공통 공개키와 짝인 개인키를 넣는다.
		@JvmStatic
		@DynamicPropertySource
		fun accountProperties(registry: DynamicPropertyRegistry) {
			registry.add("jwt.private-key") { Base64.getEncoder().encodeToString(jwtKeyPair.private.encoded) }
			registry.add("jwt.access-token-ttl") { "PT30M" }
			registry.add("jwt.refresh-token-ttl") { "P14D" }
			registry.add("spring.kafka.bootstrap-servers") { "localhost:9092" }
			registry.add("spring.kafka.admin.auto-create") { "false" }
			registry.add("spring.grpc.client.channel.family.target") { "localhost:9082" }
		}
	}
}
