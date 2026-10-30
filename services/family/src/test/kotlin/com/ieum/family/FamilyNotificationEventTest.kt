package com.ieum.family

import com.ieum.events.NotificationEvent
import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
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
import org.springframework.http.MediaType
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class FamilyNotificationEventTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val objectMapper: ObjectMapper,
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
	private val familyInvitationRepository: FamilyInvitationRepository,
) : PostgresTestSupport() {

	@Autowired
	private lateinit var events: ApplicationEvents

	private val owner = UUID.randomUUID()
	private val member = UUID.randomUUID()
	private val newbie = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		familyInvitationRepository.deleteAll()
		familyMemberRepository.deleteAll()
		familyRepository.deleteAll()
	}

	private fun published() = events.stream(NotificationEvent::class.java).toList()

	private fun createFamily(): UUID {
		val familyId = requireNotNull(familyRepository.save(Family(name = "우리집")).familyId)
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = owner, role = FamilyRole.OWNER))
		return familyId
	}

	private fun familyEvent(type: NotificationEventType, recipients: List<UUID>, familyId: UUID, actor: UUID, role: String? = null) =
		NotificationEvent(
			type,
			NotificationEventData(
				recipientAccountIds = recipients,
				familyId = familyId,
				targetType = NotificationTargetType.FAMILY,
				targetId = familyId,
				actorAccountId = actor,
				role = role,
			),
		)

	@Test
	fun `초대를 수락하면 기존 구성원에게 알린다`() {
		val familyId = createFamily()
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = member, role = FamilyRole.MEMBER))
		val response = mockMvc.post("/api/v1/family/$familyId/invitation") {
			header("Authorization", "Bearer ${accessToken(owner)}")
		}.andReturn().response.contentAsString
		val code = objectMapper.readTree(response).get("data").get("code").asString()

		mockMvc.post("/api/v1/family-invitation/$code/accept") { header("Authorization", "Bearer ${accessToken(newbie)}") }

		assertEquals(listOf(familyEvent(NotificationEventType.FAMILY_MEMBER_JOINED, listOf(owner, member), familyId, newbie)), published())
	}

	private fun changeRole(familyId: UUID, familyMemberId: UUID?, role: String) =
		mockMvc.patch("/api/v1/family/$familyId/member/$familyMemberId/role") {
			header("Authorization", "Bearer ${accessToken(owner)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"role":"$role"}"""
		}

	@Test
	fun `역할이 바뀌면 당사자에게 새 역할을 알린다`() {
		val familyId = createFamily()
		val target = familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = member, role = FamilyRole.MEMBER))

		changeRole(familyId, target.familyMemberId, "MANAGER")
		changeRole(familyId, target.familyMemberId, "MANAGER")

		assertEquals(listOf(familyEvent(NotificationEventType.FAMILY_ROLE_CHANGED, listOf(member), familyId, owner, role = "MANAGER")), published())
	}
}
