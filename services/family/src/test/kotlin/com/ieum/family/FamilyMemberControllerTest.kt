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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
class FamilyMemberControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
	private val familyInvitationRepository: FamilyInvitationRepository,
) : PostgresTestSupport() {

	private val ownerId = UUID.randomUUID()
	private val memberId = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		familyInvitationRepository.deleteAll()
		familyMemberRepository.deleteAll()
		familyRepository.deleteAll()
	}

	private fun createFamily(): UUID {
		val familyId = requireNotNull(familyRepository.save(Family(name = "우리집")).familyId)
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = ownerId, role = FamilyRole.OWNER))
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = memberId, role = FamilyRole.MEMBER))
		return familyId
	}

	private fun memberIdOf(familyId: UUID, accountId: UUID): UUID =
		requireNotNull(familyMemberRepository.findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, accountId)?.familyMemberId)

	private fun updateRole(familyId: UUID, familyMemberId: UUID, role: String, accountId: UUID = ownerId) =
		mockMvc.patch("/api/v1/family/$familyId/member/$familyMemberId/role") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"role":"$role"}"""
		}

	private fun getMembers(familyId: UUID, accountId: UUID) = mockMvc.get("/api/v1/family/$familyId/member") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	@Test
	fun `구성원은 가입 순서대로 구성원 목록을 조회한다`() {
		val familyId = createFamily()

		getMembers(familyId, memberId).andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(2) }
			jsonPath("\$.data[0].accountId") { value(ownerId.toString()) }
			jsonPath("\$.data[0].role") { value("OWNER") }
			jsonPath("\$.data[0].familyMemberId") { exists() }
			jsonPath("\$.data[0].joinedAt") { exists() }
			jsonPath("\$.data[1].accountId") { value(memberId.toString()) }
			jsonPath("\$.data[1].role") { value("MEMBER") }
		}
	}

	@Test
	fun `구성원이 아니면 404`() {
		val familyId = createFamily()

		getMembers(familyId, UUID.randomUUID()).andExpect { status { isNotFound() } }
	}

	@Test
	fun `없는 가족이면 404`() {
		getMembers(UUID.randomUUID(), ownerId).andExpect { status { isNotFound() } }
	}

	@Test
	fun `OWNER는 구성원을 MANAGER로 바꾸고 다시 MEMBER로 돌린다`() {
		val familyId = createFamily()
		val memberFamilyMemberId = memberIdOf(familyId, memberId)

		updateRole(familyId, memberFamilyMemberId, "MANAGER").andExpect {
			status { isOk() }
			jsonPath("\$.data.familyMemberId") { value(memberFamilyMemberId.toString()) }
			jsonPath("\$.data.role") { value("MANAGER") }
		}
		updateRole(familyId, memberFamilyMemberId, "MEMBER").andExpect {
			status { isOk() }
			jsonPath("\$.data.role") { value("MEMBER") }
		}
	}

	@Test
	fun `OWNER가 아니면 403이고 MANAGER도 바꿀 수 없다`() {
		val familyId = createFamily()
		val memberFamilyMemberId = memberIdOf(familyId, memberId)
		updateRole(familyId, memberFamilyMemberId, "MANAGER")

		updateRole(familyId, memberFamilyMemberId, "MEMBER", accountId = memberId).andExpect { status { isForbidden() } }
	}

	@Test
	fun `구성원이 아니면 역할도 바꿀 수 없다 404`() {
		val familyId = createFamily()

		updateRole(familyId, memberIdOf(familyId, memberId), "MANAGER", accountId = UUID.randomUUID())
			.andExpect { status { isNotFound() } }
	}

	@Test
	fun `OWNER 역할은 주거나 바꿀 수 없다`() {
		val familyId = createFamily()

		updateRole(familyId, memberIdOf(familyId, memberId), "OWNER").andExpect { status { isBadRequest() } }
		updateRole(familyId, memberIdOf(familyId, ownerId), "MEMBER").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `다른 가족의 구성원이나 없는 구성원은 404`() {
		val familyId = createFamily()
		val otherFamilyId = requireNotNull(familyRepository.save(Family(name = "옆집")).familyId)
		val otherMember = familyMemberRepository.save(
			FamilyMember(familyId = otherFamilyId, accountId = UUID.randomUUID(), role = FamilyRole.MEMBER),
		)

		updateRole(familyId, requireNotNull(otherMember.familyMemberId), "MANAGER").andExpect { status { isNotFound() } }
		updateRole(familyId, UUID.randomUUID(), "MANAGER").andExpect { status { isNotFound() } }
	}

	@Test
	fun `없는 역할이면 400`() {
		val familyId = createFamily()

		updateRole(familyId, memberIdOf(familyId, memberId), "ADMIN").andExpect { status { isBadRequest() } }
	}

	private fun leave(familyId: UUID, accountId: UUID) = mockMvc.post("/api/v1/family/$familyId/leave") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	private fun remove(familyId: UUID, familyMemberId: UUID, accountId: UUID = ownerId) =
		mockMvc.delete("/api/v1/family/$familyId/member/$familyMemberId") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
		}

	private fun transferOwnership(familyId: UUID, familyMemberId: UUID, accountId: UUID = ownerId) =
		mockMvc.post("/api/v1/family/$familyId/member/$familyMemberId/transfer-ownership") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
		}

	private fun roleOf(familyId: UUID, accountId: UUID) =
		familyMemberRepository.findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, accountId)?.role

	@Test
	fun `구성원은 가족에서 나간다`() {
		val familyId = createFamily()

		leave(familyId, memberId).andExpect { status { isNoContent() } }

		assertNull(roleOf(familyId, memberId))
		getMembers(familyId, ownerId).andExpect { jsonPath("\$.data.length()") { value(1) } }
	}

	@Test
	fun `OWNER는 다른 구성원이 있으면 소유권을 넘겨야 나갈 수 있다`() {
		val familyId = createFamily()

		leave(familyId, ownerId).andExpect { status { isConflict() } }

		assertEquals(FamilyRole.OWNER, roleOf(familyId, ownerId))
	}

	@Test
	fun `혼자 남은 OWNER가 나가면 가족과 초대 코드가 삭제된다`() {
		val familyId = requireNotNull(familyRepository.save(Family(name = "자취방")).familyId)
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = ownerId, role = FamilyRole.OWNER))
		familyInvitationRepository.save(FamilyInvitation(familyId = familyId, code = "ABCD1234", expiresAt = LocalDateTime.now().plusDays(1)))

		leave(familyId, ownerId).andExpect { status { isNoContent() } }

		assertNull(roleOf(familyId, ownerId))
		assertNull(familyRepository.findByFamilyIdAndIsDeletedFalse(familyId))
		assertNull(familyInvitationRepository.findByFamilyIdAndIsDeletedFalse(familyId))
	}

	@Test
	fun `구성원이 아니면 나갈 수 없다 404`() {
		val familyId = createFamily()

		leave(familyId, UUID.randomUUID()).andExpect { status { isNotFound() } }
	}

	@Test
	fun `OWNER는 구성원을 내보내고 내보낸 사람은 다시 들어올 수 있다`() {
		val familyId = createFamily()

		remove(familyId, memberIdOf(familyId, memberId)).andExpect { status { isNoContent() } }

		assertNull(roleOf(familyId, memberId))
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = memberId, role = FamilyRole.MEMBER))
		assertEquals(FamilyRole.MEMBER, roleOf(familyId, memberId))
	}

	@Test
	fun `OWNER만 내보낼 수 있고 OWNER는 내보낼 수 없다`() {
		val familyId = createFamily()
		val managerId = UUID.randomUUID()
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = managerId, role = FamilyRole.MANAGER))

		remove(familyId, memberIdOf(familyId, memberId), accountId = managerId).andExpect { status { isForbidden() } }
		remove(familyId, memberIdOf(familyId, ownerId)).andExpect { status { isBadRequest() } }
		remove(familyId, UUID.randomUUID()).andExpect { status { isNotFound() } }
		remove(familyId, memberIdOf(familyId, memberId), accountId = UUID.randomUUID()).andExpect { status { isNotFound() } }
	}

	@Test
	fun `OWNER는 소유권을 넘기고 MEMBER가 된다`() {
		val familyId = createFamily()

		transferOwnership(familyId, memberIdOf(familyId, memberId)).andExpect { status { isNoContent() } }

		assertEquals(FamilyRole.OWNER, roleOf(familyId, memberId))
		assertEquals(FamilyRole.MEMBER, roleOf(familyId, ownerId))
		leave(familyId, ownerId).andExpect { status { isNoContent() } }
	}

	@Test
	fun `OWNER만 소유권을 넘길 수 있고 자신에게는 넘길 수 없다`() {
		val familyId = createFamily()

		transferOwnership(familyId, memberIdOf(familyId, ownerId), accountId = memberId).andExpect { status { isForbidden() } }
		transferOwnership(familyId, memberIdOf(familyId, ownerId)).andExpect { status { isBadRequest() } }
		transferOwnership(familyId, UUID.randomUUID()).andExpect { status { isNotFound() } }

		assertEquals(FamilyRole.OWNER, roleOf(familyId, ownerId))
	}
}
