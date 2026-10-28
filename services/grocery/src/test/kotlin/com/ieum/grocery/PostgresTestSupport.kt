package com.ieum.grocery

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.MountableFile

abstract class PostgresTestSupport {

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
	}
}
