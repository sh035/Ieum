package com.ieum.test

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.MountableFile
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.UUID

// 서비스 테스트 공통 기반이다. 서비스별 설정은 상속한 클래스의 @DynamicPropertySource 에 더한다.
abstract class IeumTestSupport {

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

		val jwtKeyPair: KeyPair = KeyPairGenerator.getInstance("RSA")
			.apply { initialize(2048) }
			.generateKeyPair()

		// account가 발급하는 토큰과 같은 형태로 서명한다.
		private val jwtEncoder = NimbusJwtEncoder
			.withKeyPair(jwtKeyPair.public as RSAPublicKey, jwtKeyPair.private as RSAPrivateKey)
			.build()

		@JvmStatic
		@DynamicPropertySource
		fun jwtKeys(registry: DynamicPropertyRegistry) {
			registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(jwtKeyPair.public.encoded) }
			// 컨텍스트마다 gRPC 서버가 뜨므로, 앞 컨텍스트가 포트를 늦게 놓아도 충돌하지 않게 빈 포트를 쓴다.
			registry.add("spring.grpc.server.port") { "0" }
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
