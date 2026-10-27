package com.ieum.account

import com.ieum.account.domain.entity.Account
import com.ieum.account.repository.AccountRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val accountRepository: AccountRepository,
	private val objectMapper: ObjectMapper,
) : PostgresRedisTestSupport() {

	@BeforeEach
	fun clean() {
		accountRepository.deleteAll()
	}

	private fun saveAccount(email: String, nickname: String): Account =
		accountRepository.save(
			Account(email = email, password = "hashed", nickname = nickname, birthDate = LocalDate.of(1995, 3, 21)),
		)

	private fun accessToken(): String {
		mockMvc.post("/api/v1/auth/sign-up") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"email":"me@ieum.com","password":"Pass123!","nickname":"me","birthDate":"1995-03-21"}"""
		}
		val response = mockMvc.post("/api/v1/auth/login") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"email":"me@ieum.com","password":"Pass123!"}"""
		}.andReturn().response.contentAsString

		return objectMapper.readTree(response).get("data").get("accessToken").asString()
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
