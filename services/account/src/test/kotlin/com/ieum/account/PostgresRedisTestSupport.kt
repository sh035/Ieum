package com.ieum.account

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.MountableFile
import java.security.KeyPairGenerator
import java.util.Base64

@Testcontainers
abstract class PostgresRedisTestSupport {

	companion object {
		@Container
		@ServiceConnection
		@JvmStatic
		val postgres = PostgreSQLContainer("postgres:18")
			.withDatabaseName("ieum")
			.withCopyFileToContainer(
				MountableFile.forHostPath("../../infra/docker/schema.sql"),
				"/docker-entrypoint-initdb.d/schema.sql",
			)

		@Container
		@ServiceConnection
		@JvmStatic
		val redis = GenericContainer("redis:8")
			.withExposedPorts(6379)

		@JvmStatic
		@DynamicPropertySource
		fun jwtKeys(registry: DynamicPropertyRegistry) {
			val keyPair = KeyPairGenerator.getInstance("RSA")
				.apply { initialize(2048) }
				.generateKeyPair()

			registry.add("jwt.private-key") { Base64.getEncoder().encodeToString(keyPair.private.encoded) }
			registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
			registry.add("jwt.access-token-ttl") { "PT30M" }
			registry.add("jwt.refresh-token-ttl") { "P14D" }
		}
	}
}
