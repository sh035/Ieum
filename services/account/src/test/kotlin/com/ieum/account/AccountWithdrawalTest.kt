package com.ieum.account

import com.ieum.account.repository.AccountRepository
import com.ieum.events.Topics
import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.grpc.client.family.FamilySummary
import com.ieum.grpc.client.family.LeaveAllFamiliesResult
import com.ieum.grpc.client.family.WithdrawalCheck
import com.ieum.web.exception.IeumException
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.eq
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class AccountWithdrawalTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val accountRepository: AccountRepository,
	private val redisTemplate: StringRedisTemplate,
	private val objectMapper: ObjectMapper,
	private val entityManager: EntityManager,
	private val transactionTemplate: TransactionTemplate,
) : PostgresRedisTestSupport() {

	private val home = FamilySummary(familyId = UUID.randomUUID(), name = "우리집")
	private val solo = FamilySummary(familyId = UUID.randomUUID(), name = "혼자")

	@BeforeEach
	fun clean() {
		accountRepository.deleteAll()
		redisTemplate.connectionFactory!!.connection.serverCommands().flushAll()
	}

	private fun post(uri: String, body: String) = mockMvc.post(uri) {
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private fun signUp() =
		post("/api/v1/auth/sign-up", """{"email":"me@ieum.com","password":"Pass123!","nickname":"me","birthDate":"1995-03-21"}""")

	private fun login() = post("/api/v1/auth/login", """{"email":"me@ieum.com","password":"Pass123!"}""")

	// 가입하고 로그인해서 토큰과 계정 ID 를 돌려준다.
	private fun signedUp(): Triple<String, String, UUID> {
		signUp().andExpect { status { isCreated() } }
		val data = objectMapper.readTree(login().andExpect { status { isOk() } }.andReturn().response.contentAsString).get("data")
		val accountId = requireNotNull(accountRepository.findAll().single().accountId)
		return Triple(data.get("accessToken").asString(), data.get("refreshToken").asString(), accountId)
	}

	private fun withdraw(accessToken: String, password: String = "Pass123!", deleteSoloFamilies: Boolean = false) =
		mockMvc.delete("/api/v1/account/me") {
			header("Authorization", "Bearer $accessToken")
			contentType = MediaType.APPLICATION_JSON
			content = """{"password":"$password","deleteSoloFamilies":$deleteSoloFamilies}"""
		}

	private fun leaveResult(left: Boolean, transferRequired: List<FamilySummary> = emptyList(), solo: List<FamilySummary> = emptyList()) =
		LeaveAllFamiliesResult(left = left, check = WithdrawalCheck(transferRequired, solo))

	private fun withdrawnAccount() = accountRepository.findAll().single()

	@Test
	fun `탈퇴 전 확인은 family 가 알려준 가족을 그대로 보여준다`() {
		val (accessToken, _, accountId) = signedUp()
		`when`(familyClient.checkWithdrawal(accountId)).thenReturn(WithdrawalCheck(listOf(home), listOf(solo)))

		mockMvc.get("/api/v1/account/me/withdrawal") { header("Authorization", "Bearer $accessToken") }.andExpect {
			status { isOk() }
			jsonPath("\$.data.transferRequiredFamilies[0].familyId") { value(home.familyId.toString()) }
			jsonPath("\$.data.transferRequiredFamilies[0].name") { value("우리집") }
			jsonPath("\$.data.soloFamilies[0].name") { value("혼자") }
		}
	}

	@Test
	fun `탈퇴하면 계정을 지우고 토큰을 끊고 탈퇴 이벤트를 보낸다`() {
		val (accessToken, refreshToken, accountId) = signedUp()
		`when`(familyClient.leaveAllFamilies(accountId, true)).thenReturn(leaveResult(left = true, solo = listOf(solo)))

		withdraw(accessToken, deleteSoloFamilies = true).andExpect { status { isNoContent() } }

		assertTrue(withdrawnAccount().isDeleted)
		post("/api/v1/auth/refresh", """{"refreshToken":"$refreshToken"}""").andExpect { status { isUnauthorized() } }
		login().andExpect { status { isUnauthorized() } }
		mockMvc.get("/api/v1/account/me") { header("Authorization", "Bearer $accessToken") }.andExpect { status { isNotFound() } }

		val message = ArgumentCaptor.forClass(String::class.java)
		verify(kafkaTemplate).send(eq(Topics.ACCOUNT), eq(accountId.toString()), message.capture())
		val event = objectMapper.readTree(message.value)
		assertEquals("ACCOUNT_DELETED", event.get("eventType").asString())
		assertEquals(accountId.toString(), event.get("data").get("accountId").asString())
	}

	@Test
	fun `비밀번호가 틀리면 400 이고 가족에서 나가지 않는다`() {
		val (accessToken, _, _) = signedUp()

		withdraw(accessToken, password = "Wrong123!").andExpect { status { isBadRequest() } }

		verifyNoInteractions(familyClient)
		assertFalse(withdrawnAccount().isDeleted)
	}

	@Test
	fun `소유권을 넘겨야 하거나 혼자인 가족 삭제에 동의하지 않으면 409 이고 계정은 남는다`() {
		val (accessToken, _, accountId) = signedUp()
		`when`(familyClient.leaveAllFamilies(accountId, true)).thenReturn(leaveResult(left = false, transferRequired = listOf(home)))
		`when`(familyClient.leaveAllFamilies(accountId, false)).thenReturn(leaveResult(left = false, solo = listOf(solo)))

		withdraw(accessToken, deleteSoloFamilies = true).andExpect {
			status { isConflict() }
			jsonPath("\$.message") { value("Transfer ownership of your families before withdrawal.") }
		}
		withdraw(accessToken, deleteSoloFamilies = false).andExpect {
			status { isConflict() }
			jsonPath("\$.message") { value("Confirm deleting the families you are alone in.") }
		}

		assertFalse(withdrawnAccount().isDeleted)
		verifyNoInteractions(kafkaTemplate)
	}

	@Test
	fun `family 장애면 503 이고 계정은 남는다`() {
		val (accessToken, _, accountId) = signedUp()
		`when`(familyClient.leaveAllFamilies(accountId, false)).thenThrow(IeumException(GrpcClientErrorCode.FAMILY_SERVICE_UNAVAILABLE))

		withdraw(accessToken).andExpect { status { isServiceUnavailable() } }

		assertFalse(withdrawnAccount().isDeleted)
	}

	@Test
	fun `같은 이메일은 탈퇴 3일 뒤부터 다시 가입할 수 있다`() {
		val (accessToken, _, accountId) = signedUp()
		`when`(familyClient.leaveAllFamilies(accountId, false)).thenReturn(leaveResult(left = true))
		withdraw(accessToken).andExpect { status { isNoContent() } }

		signUp().andExpect {
			status { isConflict() }
			jsonPath("\$.message") { value("You can sign up again with this email 3 days after withdrawal.") }
		}

		withdrawnAt(LocalDateTime.now().minusDays(3).minusMinutes(1), accountId)
		signUp().andExpect { status { isCreated() } }
		login().andExpect { status { isOk() } }
	}

	private fun withdrawnAt(at: LocalDateTime, accountId: UUID) {
		transactionTemplate.executeWithoutResult {
			entityManager.createNativeQuery("update account.account set updated_at = :at where account_id = :id")
				.setParameter("at", at)
				.setParameter("id", accountId)
				.executeUpdate()
		}
	}
}
