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

	private fun accessToken(): String = run { signUp(); login() }.get("accessToken").asString()

	private fun post(uri: String, body: String) = mockMvc.post(uri) {
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private fun signUp(email: String = "me@ieum.com") {
		post("/api/v1/auth/sign-up", """{"email":"$email","password":"Pass123!","nickname":"me","birthDate":"1995-03-21"}""")
			.andExpect { status { isCreated() } }
	}

	private fun login(email: String = "me@ieum.com") = run {
		val response = post("/api/v1/auth/login", """{"email":"$email","password":"Pass123!"}""")
			.andExpect { status { isOk() } }
			.andReturn().response.contentAsString

		objectMapper.readTree(response).get("data")
	}

	private fun updateMe(accessToken: String, body: String) = mockMvc.patch("/api/v1/account/me") {
		header("Authorization", "Bearer $accessToken")
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private fun refresh(refreshToken: String) = post("/api/v1/auth/refresh", """{"refreshToken":"$refreshToken"}""")

	private fun changePassword(accessToken: String, current: String, new: String) =
		updateMe(accessToken, """{"currentPassword":"$current","newPassword":"$new"}""")

	@Test
	fun `update changes only the sent fields`() {
		val token = accessToken()

		updateMe(token, """{"nickname":"tobi"}""").andExpect {
			status { isOk() }
			jsonPath("\$.data.account.nickname") { value("tobi") }
			jsonPath("\$.data.account.birthDate") { value("1995-03-21") }
			jsonPath("\$.data.account.email") { value("me@ieum.com") }
			jsonPath("\$.data.token") { value(null) }
		}
		updateMe(token, """{"birthDate":"2000-01-02"}""").andExpect { status { isOk() } }

		val saved = accountRepository.findAll().single()
		assertEquals("tobi", saved.nickname)
		assertEquals(LocalDate.of(2000, 1, 2), saved.birthDate)
		post("/api/v1/auth/login", """{"email":"me@ieum.com","password":"Pass123!"}""").andExpect { status { isOk() } }
	}

	@Test
	fun `invalid nickname or future birth date returns 400 and changes nothing`() {
		val token = accessToken()

		updateMe(token, """{"nickname":" "}""").andExpect { status { isBadRequest() } }
		updateMe(token, """{"nickname":"${"a".repeat(21)}"}""").andExpect { status { isBadRequest() } }
		updateMe(token, """{"nickname":"tobi","birthDate":"2999-01-01"}""").andExpect { status { isBadRequest() } }

		val saved = accountRepository.findAll().single()
		assertEquals("me", saved.nickname)
		assertEquals(LocalDate.of(1995, 3, 21), saved.birthDate)
	}

	@Test
	fun `password change revokes every refresh token and issues new tokens`() {
		signUp()
		signUp("other@ieum.com")
		val phone = login()
		val tablet = login()
		val other = login("other@ieum.com")

		val response = updateMe(phone.get("accessToken").asString(), """{"nickname":"tobi","currentPassword":"Pass123!","newPassword":"NewPass1!"}""")
			.andExpect {
				status { isOk() }
				jsonPath("\$.data.account.nickname") { value("tobi") }
			}
			.andReturn().response.contentAsString
		val renewed = objectMapper.readTree(response).get("data").get("token")

		refresh(phone.get("refreshToken").asString()).andExpect { status { isUnauthorized() } }
		refresh(tablet.get("refreshToken").asString()).andExpect { status { isUnauthorized() } }
		refresh(renewed.get("refreshToken").asString()).andExpect { status { isOk() } }
		refresh(other.get("refreshToken").asString()).andExpect { status { isOk() } }
		post("/api/v1/auth/login", """{"email":"me@ieum.com","password":"Pass123!"}""").andExpect { status { isUnauthorized() } }
		post("/api/v1/auth/login", """{"email":"me@ieum.com","password":"NewPass1!"}""").andExpect { status { isOk() } }
	}

	@Test
	fun `wrong or missing current password returns 400 and changes nothing`() {
		signUp()
		val session = login()
		val token = session.get("accessToken").asString()

		changePassword(token, "Wrong123!", "NewPass1!").andExpect {
			status { isBadRequest() }
			jsonPath("\$.message") { value("Current password is incorrect.") }
		}
		updateMe(token, """{"nickname":"tobi","newPassword":"NewPass1!"}""").andExpect {
			status { isBadRequest() }
			jsonPath("\$.message") { value("Current password is required.") }
		}

		assertEquals("me", accountRepository.findAll().single().nickname)
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
