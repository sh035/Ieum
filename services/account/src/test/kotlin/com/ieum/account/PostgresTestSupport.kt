package com.ieum.account

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.MountableFile
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.util.Base64

@Testcontainers
abstract class PostgresTestSupport {

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

		@JvmStatic
		@DynamicPropertySource
		fun jwtKeys(registry: DynamicPropertyRegistry) {
			val keyPair = KeyPairGenerator.getInstance("RSA")
				.apply { initialize(2048) }
				.generateKeyPair()

			val privateKey = writePem("PRIVATE KEY", keyPair.private.encoded)
			val publicKey = writePem("PUBLIC KEY", keyPair.public.encoded)

			registry.add("jwt.private-key") { "file:$privateKey" }
			registry.add("jwt.public-key") { "file:$publicKey" }
			registry.add("jwt.access-token-ttl") { "PT30M" }
			registry.add("jwt.refresh-token-ttl") { "P14D" }
		}

		private fun writePem(type: String, encoded: ByteArray): String {
			val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(encoded)
			val pem = "-----BEGIN $type-----\n$body\n-----END $type-----\n"
			val file = Files.createTempFile("ieum-test-", ".pem").toFile()
				.apply { deleteOnExit() }

			file.writeText(pem)

			return file.absolutePath
		}
	}
}
