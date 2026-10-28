package com.ieum.planner

import com.ieum.planner.service.FamilyClient
import com.ieum.web.exception.IeumException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
class FamilyClientTimeoutTest @Autowired constructor(
	private val familyClient: FamilyClient,
) : PostgresTestSupport() {

	companion object {
		// 연결은 받지만 응답하지 않는 family를 흉내 낸다.
		private val silentServer = ServerSocket(0)
		private val openSockets = mutableListOf<Socket>()

		init {
			thread(isDaemon = true) {
				while (true) openSockets += silentServer.accept()
			}
		}

		@JvmStatic
		@DynamicPropertySource
		fun familyUrl(registry: DynamicPropertyRegistry) {
			registry.add("family.url") { "http://localhost:${silentServer.localPort}" }
		}
	}

	@Test
	@Timeout(10, unit = TimeUnit.SECONDS)
	fun `family가 응답하지 않으면 타임아웃 후 503`() {
		val e = assertFailsWith<IeumException> { familyClient.isMember(UUID.randomUUID(), UUID.randomUUID()) }

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.errorCode.status)
	}
}
