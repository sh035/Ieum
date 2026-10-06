package com.ieum.account

import com.ieum.account.repository.AccountRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val accountRepository: AccountRepository,
) : PostgresTestSupport() {

	@BeforeEach
	fun clean() {
		accountRepository.deleteAll()
	}

	private fun signUp(body: String) = mockMvc.post("/api/v1/auth/sign-up") {
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private fun request(email: String = "tobi@ieum.com") = """
		{"email":"$email","password":"Pass123!","nickname":"tobi","birthDate":"1995-03-21"}
	""".trimIndent()

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
}
