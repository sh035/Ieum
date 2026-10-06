package com.ieum.account

import com.ieum.account.controller.dto.response.TokenResponse
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
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val accountRepository: AccountRepository,
	private val redisTemplate: StringRedisTemplate,
	private val objectMapper: ObjectMapper,
) : PostgresRedisTestSupport() {

	@BeforeEach
	fun clean() {
		accountRepository.deleteAll()
		redisTemplate.connectionFactory!!.connection.serverCommands().flushAll()
	}

	private fun signUp(body: String) = mockMvc.post("/api/v1/auth/sign-up") {
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private fun request(email: String = "tobi@ieum.com") = """
		{"email":"$email","password":"Pass123!","nickname":"tobi","birthDate":"1995-03-21"}
	""".trimIndent()

	private fun post(uri: String, body: String) = mockMvc.post(uri) {
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private fun login(email: String = "tobi@ieum.com", password: String = "Pass123!"): TokenResponse {
		val response = post("/api/v1/auth/login", """{"email":"$email","password":"$password"}""")
			.andExpect { status { isOk() } }
			.andReturn().response.contentAsString

		return objectMapper.readValue(response, TokenResponse::class.java)
	}

	private fun signedUp(): TokenResponse {
		signUp(request()).andExpect { status { isCreated() } }

		return login()
	}

	@Test
	fun `sign up returns 201 with empty body`() {
		signUp(request()).andExpect {
			status { isCreated() }
			content { string("") }
		}

		val saved = accountRepository.findAll().single()
		assertEquals("tobi@ieum.com", saved.email)
		assertTrue(saved.password.startsWith("\$2a\$"), "password must be bcrypt hashed")
		assertEquals(false, saved.isDeleted)
	}

	@Test
	fun `duplicate email returns 409`() {
		signUp(request()).andExpect { status { isCreated() } }

		signUp(request()).andExpect {
			status { isConflict() }
			jsonPath("\$.code") { value(409) }
			jsonPath("\$.message") { value("Email already exists.") }
			jsonPath("\$.timestamp") { exists() }
		}

		assertEquals(1, accountRepository.count())
	}

	@Test
	fun `invalid request returns 400`() {
		signUp(request(email = "notanemail")).andExpect {
			status { isBadRequest() }
			jsonPath("\$.code") { value(400) }
		}

		assertEquals(0, accountRepository.count())
	}

	@Test
	fun `login returns both tokens`() {
		val token = signedUp()

		assertEquals(3, token.accessToken.split(".").size, "access token must be a JWT")
		assertTrue(token.refreshToken.isNotBlank())
		assertEquals(1, redisTemplate.keys("refresh:*").size)
	}

	@Test
	fun `wrong password and unknown email return the same 401`() {
		signUp(request()).andExpect { status { isCreated() } }

		val wrongPassword = post("/api/v1/auth/login", """{"email":"tobi@ieum.com","password":"Wrong123!"}""")
			.andExpect {
				status { isUnauthorized() }
				jsonPath("\$.message") { value("Invalid credentials.") }
			}.andReturn().response.contentAsString

		val unknownEmail = post("/api/v1/auth/login", """{"email":"nobody@ieum.com","password":"Pass123!"}""")
			.andExpect { status { isUnauthorized() } }
			.andReturn().response.contentAsString

		assertEquals(
			objectMapper.readTree(wrongPassword).get("message"),
			objectMapper.readTree(unknownEmail).get("message"),
			"failure message must not reveal whether the account exists",
		)
	}

	@Test
	fun `refresh token is stored hashed, not raw`() {
		val token = signedUp()

		val key = redisTemplate.keys("refresh:*").single()
		assertNotEquals("refresh:${token.refreshToken}", key)
		assertEquals(64, key.removePrefix("refresh:").length, "key must be a SHA-256 hex digest")
	}

	@Test
	fun `reissue returns a new access token and keeps the refresh token`() {
		val token = signedUp()

		val reissued = post("/api/v1/auth/refresh", """{"refreshToken":"${token.refreshToken}"}""")
			.andExpect { status { isOk() } }
			.andReturn().response.contentAsString
			.let { objectMapper.readValue(it, TokenResponse::class.java) }

		assertEquals(token.refreshToken, reissued.refreshToken, "no rotation")
		assertTrue(reissued.accessToken.isNotBlank())
	}

	@Test
	fun `reissue with an unknown refresh token returns 401`() {
		post("/api/v1/auth/refresh", """{"refreshToken":"not-a-real-token"}""").andExpect {
			status { isUnauthorized() }
			jsonPath("\$.message") { value("Invalid credentials.") }
		}
	}

	@Test
	fun `logout revokes the refresh token`() {
		val token = signedUp()

		post("/api/v1/auth/logout", """{"refreshToken":"${token.refreshToken}"}""")
			.andExpect { status { isNoContent() } }

		assertTrue(redisTemplate.keys("refresh:*").isEmpty())

		post("/api/v1/auth/refresh", """{"refreshToken":"${token.refreshToken}"}""")
			.andExpect { status { isUnauthorized() } }
	}

	@Test
	fun `me requires an access token`() {
		val token = signedUp()

		mockMvc.get("/api/v1/account/me").andExpect { status { isUnauthorized() } }

		mockMvc.get("/api/v1/account/me") {
			header("Authorization", "Bearer ${token.accessToken}")
		}.andExpect {
			status { isOk() }
			jsonPath("\$.email") { value("tobi@ieum.com") }
			jsonPath("\$.nickname") { value("tobi") }
			jsonPath("\$.birthDate") { value("1995-03-21") }
		}
	}
}
