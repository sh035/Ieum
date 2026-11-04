package com.ieum.account

import com.ieum.test.IeumTestSupport
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import java.util.Base64

abstract class PostgresRedisTestSupport : IeumTestSupport() {

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
		}
	}
}
