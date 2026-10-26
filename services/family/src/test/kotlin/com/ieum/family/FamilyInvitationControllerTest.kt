package com.ieum.family

import com.ieum.family.domain.entity.FamilyInvitation
import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.repository.FamilyInvitationRepository
import com.ieum.family.repository.FamilyMemberRepository
import com.ieum.family.repository.FamilyRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@SpringBootTest
@AutoConfigureMockMvc
class FamilyInvitationControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
	private val familyInvitationRepository: FamilyInvitationRepository,
	private val objectMapper: ObjectMapper,
) : PostgresTestSupport() {

	@MockitoSpyBean
	private lateinit var familyMemberRepositorySpy: FamilyMemberRepository

	private val ownerId = UUID.randomUUID()
	private val inviteeId = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		familyInvitationRepository.deleteAll()
		familyMemberRepository.deleteAll()
		familyRepository.deleteAll()
	}

	private fun createFamily(name: String = "우리집"): UUID {
		val response = mockMvc.post("/api/v1/family") {
			header("Authorization", "Bearer ${accessToken(ownerId)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"name":"$name"}"""
		}.andReturn().response.contentAsString

		return UUID.fromString(objectMapper.readTree(response).get("data").get("familyId").asString())
	}

	private fun createInvitation(familyId: UUID, accountId: UUID = ownerId) =
		mockMvc.post("/api/v1/family/$familyId/invitation") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
		}

	private fun createCode(familyId: UUID): String {
		val response = createInvitation(familyId).andReturn().response.contentAsString
		return objectMapper.readTree(response).get("data").get("code").asString()
	}

	private fun verify(code: String) = mockMvc.get("/api/v1/family-invitation/$code") {
		header("Authorization", "Bearer ${accessToken(inviteeId)}")
	}

	private fun accept(code: String, accountId: UUID = inviteeId) = mockMvc.post("/api/v1/family-invitation/$code/accept") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	@Test
	fun `OWNER가 초대 코드를 만들면 8자리 코드와 만료 시각을 반환한다`() {
		val familyId = createFamily()

		createInvitation(familyId).andExpect {
			status { isCreated() }
			jsonPath("\$.data.code") { value(org.hamcrest.Matchers.matchesPattern("[A-HJ-NP-Z2-9]{8}")) }
			jsonPath("\$.data.expiresAt") { exists() }
		}
	}

	@Test
	fun `MEMBER는 초대 코드를 만들 수 없다`() {
		val familyId = createFamily()
		accept(createCode(familyId))

		createInvitation(familyId, accountId = inviteeId).andExpect {
			status { isForbidden() }
		}
	}

	@Test
	fun `구성원이 아니면 404`() {
		val familyId = createFamily()

		createInvitation(familyId, accountId = UUID.randomUUID()).andExpect {
			status { isNotFound() }
		}
	}

	@Test
	fun `새로 만들면 기존 코드는 폐기된다`() {
		val familyId = createFamily()
		val oldCode = createCode(familyId)
		val newCode = createCode(familyId)

		assertNotEquals(oldCode, newCode)
		verify(oldCode).andExpect { status { isNotFound() } }
		mockMvc.get("/api/v1/family/$familyId/invitation") {
			header("Authorization", "Bearer ${accessToken(ownerId)}")
		}.andExpect {
			status { isOk() }
			jsonPath("\$.data.code") { value(newCode) }
		}
	}

	@Test
	fun `코드를 검증하면 가족 이름을 반환한다`() {
		val familyId = createFamily("우리집")

		verify(createCode(familyId)).andExpect {
			status { isOk() }
			jsonPath("\$.data.familyId") { value(familyId.toString()) }
			jsonPath("\$.data.name") { value("우리집") }
		}
	}

	@Test
	fun `코드로 가입하면 MEMBER가 된다`() {
		val familyId = createFamily()

		accept(createCode(familyId)).andExpect {
			status { isCreated() }
			jsonPath("\$.data.role") { value("MEMBER") }
		}

		val member = familyMemberRepository.findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, inviteeId)
		assertEquals(FamilyRole.MEMBER, member?.role)
	}

	@Test
	fun `이미 구성원이면 409`() {
		val familyId = createFamily()
		val code = createCode(familyId)
		accept(code)

		accept(code).andExpect { status { isConflict() } }
		accept(code, accountId = ownerId).andExpect { status { isConflict() } }
	}

	@Test
	fun `동시 수락으로 exists 확인을 통과해도 409`() {
		val familyId = createFamily()
		val code = createCode(familyId)
		accept(code)
		doReturn(false).`when`(familyMemberRepositorySpy).existsByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, inviteeId)

		accept(code).andExpect { status { isConflict() } }
	}

	@Test
	fun `만료된 코드는 404`() {
		val familyId = createFamily()
		familyInvitationRepository.save(
			FamilyInvitation(familyId = familyId, code = "EXPIRED2", expiresAt = LocalDateTime.now().minusSeconds(1)),
		)

		verify("EXPIRED2").andExpect { status { isNotFound() } }
		accept("EXPIRED2").andExpect { status { isNotFound() } }
	}

	@Test
	fun `취소한 코드는 404`() {
		val familyId = createFamily()
		val code = createCode(familyId)

		mockMvc.delete("/api/v1/family/$familyId/invitation") {
			header("Authorization", "Bearer ${accessToken(ownerId)}")
		}.andExpect { status { isNoContent() } }

		verify(code).andExpect { status { isNotFound() } }
	}
}
