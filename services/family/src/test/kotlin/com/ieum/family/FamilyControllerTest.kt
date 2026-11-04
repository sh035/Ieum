package com.ieum.family

import com.ieum.family.domain.entity.Family
import com.ieum.family.domain.entity.FamilyInvitation
import com.ieum.family.domain.entity.FamilyMember
import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.repository.FamilyInvitationRepository
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
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
class FamilyControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
	private val familyInvitationRepository: FamilyInvitationRepository,
	private val objectMapper: ObjectMapper,
) : PostgresTestSupport() {

	private val accountId = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		familyInvitationRepository.deleteAll()
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

	private val ownerId = UUID.randomUUID()
	private val memberId = UUID.randomUUID()

	private fun saveFamily(): UUID {
		val familyId = requireNotNull(familyRepository.save(Family(name = "우리집")).familyId)
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = ownerId, role = FamilyRole.OWNER))
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = memberId, role = FamilyRole.MEMBER))
		return familyId
	}

	private fun leave(familyId: UUID, accountId: UUID) = mockMvc.post("/api/v1/family/$familyId/leave") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	private fun roleOf(familyId: UUID, accountId: UUID) =
		familyMemberRepository.findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, accountId)?.role

	private fun getMembers(familyId: UUID, accountId: UUID) = mockMvc.get("/api/v1/family/$familyId/member") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	@Test
	fun `구성원은 가족에서 나간다`() {
		val familyId = saveFamily()

		leave(familyId, memberId).andExpect { status { isNoContent() } }

		assertNull(roleOf(familyId, memberId))
		getMembers(familyId, ownerId).andExpect { jsonPath("\$.data.length()") { value(1) } }
	}

	@Test
	fun `OWNER는 다른 구성원이 있으면 소유권을 넘겨야 나갈 수 있다`() {
		val familyId = saveFamily()

		leave(familyId, ownerId).andExpect { status { isConflict() } }

		assertEquals(FamilyRole.OWNER, roleOf(familyId, ownerId))
	}

	@Test
	fun `혼자 남은 OWNER가 나가면 가족과 대기 중인 초대가 모두 삭제된다`() {
		val familyId = requireNotNull(familyRepository.save(Family(name = "자취방")).familyId)
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = ownerId, role = FamilyRole.OWNER))
		listOf(memberId, UUID.randomUUID()).forEach { invitee ->
			familyInvitationRepository.save(
				FamilyInvitation(familyId = familyId, inviterAccountId = ownerId, inviteeAccountId = invitee, expiresAt = LocalDateTime.now().plusHours(1)),
			)
		}

		leave(familyId, ownerId).andExpect { status { isNoContent() } }

		assertNull(roleOf(familyId, ownerId))
		assertNull(familyRepository.findByFamilyIdAndIsDeletedFalse(familyId))
		assertEquals(emptyList(), familyInvitationRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId))
	}

	@Test
	fun `구성원이 아니면 나갈 수 없다 404`() {
		val familyId = saveFamily()

		leave(familyId, UUID.randomUUID()).andExpect { status { isNotFound() } }
	}
}
