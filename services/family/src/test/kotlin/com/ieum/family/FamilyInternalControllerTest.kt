package com.ieum.family

import com.ieum.family.domain.entity.Family
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class FamilyInternalControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
	private val familyInvitationRepository: FamilyInvitationRepository,
) : PostgresTestSupport() {

	private val ownerId = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		familyInvitationRepository.deleteAll()
		familyMemberRepository.deleteAll()
		familyRepository.deleteAll()
	}

	private fun createFamily(): UUID {
		val familyId = requireNotNull(familyRepository.save(Family(name = "우리집")).familyId)
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = ownerId, role = FamilyRole.OWNER))
		return familyId
	}

	private fun getRole(familyId: UUID, accountId: UUID) =
		mockMvc.get("/internal/family/$familyId/member/$accountId/role")

	@Test
	fun `구성원이면 역할을 반환한다`() {
		val familyId = createFamily()

		getRole(familyId, ownerId).andExpect {
			status { isOk() }
			jsonPath("\$.data.role") { value("OWNER") }
		}
	}

	@Test
	fun `구성원이 아니면 404`() {
		val familyId = createFamily()

		getRole(familyId, UUID.randomUUID()).andExpect { status { isNotFound() } }
	}

	@Test
	fun `내보낸 구성원이면 404`() {
		val familyId = createFamily()
		val member = familyMemberRepository.save(
			FamilyMember(familyId = familyId, accountId = UUID.randomUUID(), role = FamilyRole.MEMBER),
		)
		member.delete()
		familyMemberRepository.save(member)

		getRole(familyId, member.accountId).andExpect { status { isNotFound() } }
	}
}
