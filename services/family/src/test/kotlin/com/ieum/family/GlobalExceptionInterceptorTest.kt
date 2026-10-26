package com.ieum.family

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionInterceptorTest @Autowired constructor(
	private val mockMvc: MockMvc,
) : PostgresTestSupport() {

	private val authorization = "Bearer ${accessToken(UUID.randomUUID())}"

	@Test
	fun `경로 변수가 UUID가 아니면 400`() {
		mockMvc.get("/api/v1/family/not-uuid/invitation") {
			header("Authorization", authorization)
		}.andExpect {
			status { isBadRequest() }
			jsonPath("\$.code") { value(400) }
		}
	}

	@Test
	fun `JSON 본문이 깨지면 400`() {
		mockMvc.post("/api/v1/family") {
			header("Authorization", authorization)
			contentType = MediaType.APPLICATION_JSON
			content = "{bad"
		}.andExpect { status { isBadRequest() } }
	}

	@Test
	fun `JSON 본문이 없으면 400`() {
		mockMvc.post("/api/v1/family") {
			header("Authorization", authorization)
			contentType = MediaType.APPLICATION_JSON
		}.andExpect { status { isBadRequest() } }
	}

	@Test
	fun `없는 URL이면 404`() {
		mockMvc.get("/api/v1/unknown") {
			header("Authorization", authorization)
		}.andExpect {
			status { isNotFound() }
			jsonPath("\$.code") { value(404) }
		}
	}

	@Test
	fun `지원하지 않는 메서드면 405`() {
		mockMvc.put("/api/v1/family") {
			header("Authorization", authorization)
		}.andExpect { status { isMethodNotAllowed() } }
	}
}
