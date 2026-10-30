package com.ieum.notification

import com.ieum.grpc.client.account.AccountClient
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.notification.domain.enums.DevicePlatform
import com.ieum.notification.repository.DeviceTokenRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
class DeviceTokenControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val deviceTokenRepository: DeviceTokenRepository,
) : PostgresKafkaTestSupport() {

	@MockitoBean
	private lateinit var accountClient: AccountClient

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val me = UUID.randomUUID()
	private val other = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		deviceTokenRepository.deleteAllInBatch()
	}

	private fun register(token: String, platform: String = "ANDROID", accountId: UUID = me) =
		mockMvc.post("/api/v1/notification/device-token") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"token":"$token","platform":"$platform"}"""
		}

	private fun unregister(token: String, accountId: UUID = me) =
		mockMvc.delete("/api/v1/notification/device-token") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"token":"$token"}"""
		}

	@Test
	fun `기기 토큰을 등록하고 다시 등록해도 한 건이다`() {
		register("token-a").andExpect { status { isNoContent() } }
		register("token-a", platform = "IOS").andExpect { status { isNoContent() } }

		val saved = deviceTokenRepository.findAll().single()
		assertEquals(me, saved.accountId)
		assertEquals("token-a", saved.token)
		assertEquals(DevicePlatform.IOS, saved.platform)
	}

	@Test
	fun `다른 계정이 같은 토큰을 등록하면 그 계정으로 옮긴다`() {
		register("token-a")
		register("token-a", accountId = other)

		val saved = deviceTokenRepository.findAll().single()
		assertEquals(other, saved.accountId)
	}

	@Test
	fun `내 토큰은 지우고 남의 토큰 삭제 요청은 아무 일도 없다`() {
		register("token-a")
		register("token-b", accountId = other)

		unregister("token-a").andExpect { status { isNoContent() } }
		unregister("token-b").andExpect { status { isNoContent() } }
		unregister("unknown").andExpect { status { isNoContent() } }

		assertNull(deviceTokenRepository.findByTokenAndIsDeletedFalse("token-a"))
		assertEquals(other, deviceTokenRepository.findByTokenAndIsDeletedFalse("token-b")?.accountId)
	}

	@Test
	fun `잘못된 플랫폼이나 빈 토큰은 400`() {
		register("token-a", platform = "WINDOWS").andExpect { status { isBadRequest() } }
		register("").andExpect { status { isBadRequest() } }
	}
}
