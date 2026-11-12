package com.ieum.family

import com.ieum.family.domain.entity.Family
import com.ieum.family.domain.entity.FamilyInvitation
import com.ieum.family.domain.entity.FamilyMember
import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.repository.FamilyInvitationRepository
import com.ieum.family.repository.FamilyMemberRepository
import com.ieum.family.repository.FamilyRepository
import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.web.exception.IeumException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
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
	private val strangerId = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		familyInvitationRepository.deleteAll()
		familyMemberRepository.deleteAll()
		familyRepository.deleteAll()
		`when`(accountClient.getNicknames(setOf(accountId))).thenReturn(mapOf(accountId to "토비"))
		`when`(accountClient.getNicknames(setOf(strangerId))).thenReturn(mapOf(strangerId to "남"))
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
		assertEquals("토비", member.nickname)
	}

	@Test
	fun `계정 닉네임을 못 찾으면 404, account 장애면 503 이고 가족을 만들지 않는다`() {
		createFamily("우리집", accountId = UUID.randomUUID()).andExpect {
			status { isNotFound() }
			jsonPath("\$.message") { value("Account not found.") }
		}
		`when`(accountClient.getNicknames(setOf(accountId))).thenThrow(IeumException(GrpcClientErrorCode.ACCOUNT_SERVICE_UNAVAILABLE))
		createFamily("우리집").andExpect { status { isServiceUnavailable() } }

		assertEquals(0, familyRepository.count())
	}

	@Test
	fun `만든 사람과 고친 사람을 요청 토큰의 계정으로 남기고 만들 때는 고친 사람을 비운다`() {
		createFamily("우리집").andExpect { status { isCreated() } }
		val family = familyRepository.findAll().single()
		val member = familyMemberRepository.findAll().single()
		assertEquals(accountId, family.createdBy)
		assertNull(family.updatedBy)
		assertEquals(accountId, member.createdBy)
		assertNull(member.updatedBy)

		mockMvc.patch("/api/v1/family/${family.familyId}/member/me") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"nickname":"아빠"}"""
		}.andExpect { status { isOk() } }

		val updated = familyMemberRepository.findAll().single()
		assertEquals(accountId, updated.createdBy)
		assertEquals(accountId, updated.updatedBy)

		// 이벤트 처리나 스케줄러처럼 요청 밖에서 고치면 마지막 수정자는 시스템이다.
		updated.nickname = "가장"
		assertNull(familyMemberRepository.save(updated).updatedBy)
		assertEquals(accountId, familyMemberRepository.findAll().single().createdBy)
	}

	@Test
	fun `요청한 사람이 없는 저장은 만든 사람도 시스템이다`() {
		assertNull(familyRepository.save(Family(name = "우리집")).createdBy)
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
		createFamily("남의집", accountId = strangerId)

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
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = ownerId, role = FamilyRole.OWNER, nickname = "구성원"))
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = memberId, role = FamilyRole.MEMBER, nickname = "구성원"))
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
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = ownerId, role = FamilyRole.OWNER, nickname = "구성원"))
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
