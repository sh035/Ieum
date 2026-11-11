package com.ieum.account

import com.ieum.account.domain.entity.Account
import com.ieum.account.repository.AccountRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val accountRepository: AccountRepository,
	private val objectMapper: ObjectMapper,
	private val redisTemplate: StringRedisTemplate,
) : PostgresRedisTestSupport() {

	@BeforeEach
	fun clean() {
		accountRepository.deleteAll()
		redisTemplate.connectionFactory!!.connection.serverCommands().flushAll()
	}

	private fun saveAccount(email: String, nickname: String): Account =
		accountRepository.save(
			Account(email = email, password = "hashed", nickname = nickname, birthDate = LocalDate.of(1995, 3, 21)),
		)

	private fun accessToken(): String = login().get("accessToken").asString()

	private fun post(uri: String, body: String) = mockMvc.post(uri) {
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private fun login(password: String = "Pass123!") = run {
		post("/api/v1/auth/sign-up", """{"email":"me@ieum.com","password":"Pass123!","nickname":"me","birthDate":"1995-03-21"}""")
		val response = post("/api/v1/auth/login", """{"email":"me@ieum.com","password":"$password"}""")
			.andReturn().response.contentAsString

		objectMapper.readTree(response).get("data")
	}

	private fun patch(uri: String, accessToken: String, body: String) = mockMvc.patch(uri) {
		header("Authorization", "Bearer $accessToken")
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private fun refresh(refreshToken: String) = post("/api/v1/auth/refresh", """{"refreshToken":"$refreshToken"}""")

	private fun changePassword(accessToken: String, current: String, new: String) =
		patch("/api/v1/account/me/password", accessToken, """{"currentPassword":"$current","newPassword":"$new"}""")

	@Test
	fun `nickname change returns the updated account`() {
		val token = accessToken()

		patch("/api/v1/account/me", token, """{"nickname":"tobi"}""").andExpect {
			status { isOk() }
			jsonPath("\$.data.nickname") { value("tobi") }
			jsonPath("\$.data.email") { value("me@ieum.com") }
		}
		assertEquals("tobi", accountRepository.findAll().single().nickname)
	}

	@Test
	fun `blank or too long nickname returns 400`() {
		val token = accessToken()

		patch("/api/v1/account/me", token, """{"nickname":" "}""").andExpect { status { isBadRequest() } }
		patch("/api/v1/account/me", token, """{"nickname":"${"a".repeat(21)}"}""").andExpect { status { isBadRequest() } }
		assertEquals("me", accountRepository.findAll().single().nickname)
	}

	@Test
	fun `password change revokes every refresh token and issues new tokens`() {
		val phone = login()
		val tablet = login()

		val response = changePassword(phone.get("accessToken").asString(), "Pass123!", "NewPass1!")
			.andExpect { status { isOk() } }
			.andReturn().response.contentAsString
		val renewed = objectMapper.readTree(response).get("data")

		refresh(phone.get("refreshToken").asString()).andExpect { status { isUnauthorized() } }
		refresh(tablet.get("refreshToken").asString()).andExpect { status { isUnauthorized() } }
		refresh(renewed.get("refreshToken").asString()).andExpect { status { isOk() } }
		post("/api/v1/auth/login", """{"email":"me@ieum.com","password":"Pass123!"}""").andExpect { status { isUnauthorized() } }
		post("/api/v1/auth/login", """{"email":"me@ieum.com","password":"NewPass1!"}""").andExpect { status { isOk() } }
	}

	@Test
	fun `wrong current password returns 400 and keeps sessions`() {
		val session = login()

		changePassword(session.get("accessToken").asString(), "Wrong123!", "NewPass1!").andExpect { status { isBadRequest() } }

		refresh(session.get("refreshToken").asString()).andExpect { status { isOk() } }
		post("/api/v1/auth/login", """{"email":"me@ieum.com","password":"Pass123!"}""").andExpect { status { isOk() } }
	}

	@Test
	fun `weak new password returns 400`() {
		changePassword(accessToken(), "Pass123!", "password").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `account update without token returns 401`() {
		mockMvc.patch("/api/v1/account/me") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"nickname":"tobi"}"""
		}.andExpect { status { isUnauthorized() } }
		mockMvc.patch("/api/v1/account/me/password") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"currentPassword":"Pass123!","newPassword":"NewPass1!"}"""
		}.andExpect { status { isUnauthorized() } }
	}

	private fun getProfiles(ids: String) = mockMvc.get("/api/v1/account") {
		header("Authorization", "Bearer ${accessToken()}")
		param("ids", ids)
	}

	@Test
	fun `profiles returns only accountId and nickname of existing accounts`() {
		val tobi = saveAccount("tobi@ieum.com", "tobi")
		val deleted = saveAccount("gone@ieum.com", "gone").apply { delete() }.let { accountRepository.save(it) }

		getProfiles("${tobi.accountId},${deleted.accountId},${UUID.randomUUID()}").andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(1) }
			jsonPath("\$.data[0].accountId") { value(tobi.accountId.toString()) }
			jsonPath("\$.data[0].nickname") { value("tobi") }
			jsonPath("\$.data[0].email") { doesNotExist() }
		}
	}

	@Test
	fun `profiles with more than 100 ids returns 400`() {
		getProfiles((1..101).joinToString(",") { UUID.randomUUID().toString() }).andExpect {
			status { isBadRequest() }
		}
	}

	@Test
	fun `profiles with invalid id returns 400`() {
		getProfiles("not-uuid").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `profiles without ids returns 400`() {
		mockMvc.get("/api/v1/account") {
			header("Authorization", "Bearer ${accessToken()}")
		}.andExpect { status { isBadRequest() } }
	}

	@Test
	fun `profiles without token returns 401`() {
		mockMvc.get("/api/v1/account") {
			param("ids", UUID.randomUUID().toString())
		}.andExpect { status { isUnauthorized() } }
	}
}
