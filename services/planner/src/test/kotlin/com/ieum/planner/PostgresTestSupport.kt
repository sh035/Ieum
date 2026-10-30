package com.ieum.planner

import com.ieum.events.NotificationEventRelay
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.MountableFile
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.UUID

abstract class PostgresTestSupport {

	// 이벤트는 @RecordApplicationEvents 로 확인하고 Kafka 로는 보내지 않는다.
	@MockitoBean
	lateinit var notificationEventRelay: NotificationEventRelay

	companion object {
		// 테스트 클래스마다 컨테이너를 내리면 캐시된 Spring 컨텍스트가 죽은 포트를 바라보므로 JVM 동안 하나만 띄운다.
		@ServiceConnection
		@JvmStatic
		val postgres = PostgreSQLContainer("postgres:18")
			.withDatabaseName("ieum")
			.withCopyFileToContainer(
				MountableFile.forHostPath("../../infra/docker/schema.sql"),
				"/docker-entrypoint-initdb.d/schema.sql",
			)
			.apply { start() }

		private val keyPair = KeyPairGenerator.getInstance("RSA")
			.apply { initialize(2048) }
			.generateKeyPair()

		// account가 발급하는 토큰과 같은 형태로 서명한다.
		private val jwtEncoder = NimbusJwtEncoder
			.withKeyPair(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey)
			.build()

		@JvmStatic
		@DynamicPropertySource
		fun jwtKeys(registry: DynamicPropertyRegistry) {
			registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
			registry.add("spring.kafka.bootstrap-servers") { "localhost:9092" }
		}

		fun accessToken(accountId: UUID): String {
			val now = Instant.now()
			val claims = JwtClaimsSet.builder()
				.subject(accountId.toString())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(1800))
				.build()

			return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
		}
	}
}
