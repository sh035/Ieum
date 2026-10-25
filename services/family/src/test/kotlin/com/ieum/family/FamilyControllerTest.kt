package com.ieum.family

import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.repository.FamilyMemberRepository
import com.ieum.family.repository.FamilyRepository
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
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class FamilyControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
	private val objectMapper: ObjectMapper,
) : PostgresTestSupport() {

	private val accountId = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		familyMemberRepository.deleteAll()
		familyRepository.deleteAll()
	}

	private fun createFamily(name: String, accountId: UUID = this.accountId) = mockMvc.post("/api/v1/family") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
		contentType = MediaType.APPLICATION_JSON
		content = """{"name":"$name"}"""
	}

	private fun getMyFamilies(accountId: UUID = this.accountId) = mockMvc.get("/api/v1/family") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	@Test
	fun `가족을 만들면 familyId를 반환하고 만든 사람이 OWNER가 된다`() {
		val response = createFamily("우리집")
			.andExpect {
				status { isCreated() }
				jsonPath("\$.code") { value(201) }
				jsonPath("\$.data.familyId") { exists() }
			}
			.andReturn().response.contentAsString

		val familyId = UUID.fromString(objectMapper.readTree(response).get("data").get("familyId").asString())
		val member = familyMemberRepository.findAll().single()
		assertEquals(familyId, member.familyId)
		assertEquals(accountId, member.accountId)
		assertEquals(FamilyRole.OWNER, member.role)
	}

	@Test
	fun `가족 이름이 비어 있으면 400`() {
		createFamily(" ").andExpect {
			status { isBadRequest() }
			jsonPath("\$.code") { value(400) }
		}
	}

	@Test
	fun `내 가족 목록은 내가 속한 가족만 가입 순서대로 반환한다`() {
		createFamily("우리집")
		createFamily("동호회")
		createFamily("남의집", accountId = UUID.randomUUID())

		getMyFamilies().andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(2) }
			jsonPath("\$.data[0].name") { value("우리집") }
			jsonPath("\$.data[0].role") { value("OWNER") }
			jsonPath("\$.data[1].name") { value("동호회") }
		}
	}

	@Test
	fun `속한 가족이 없으면 빈 배열`() {
		getMyFamilies().andExpect {
			status { isOk() }
			jsonPath("\$.data") { isArray() }
			jsonPath("\$.data.length()") { value(0) }
		}
	}
}
