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
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
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
		val invitationId = invite(familyId)

		mockMvc.post("/api/v1/family-invitation/$invitationId/accept") { header("Authorization", "Bearer ${accessToken(newbie)}") }

		assertEquals(
			listOf(
				invitedEvent(familyId, invitationId),
				familyEvent(NotificationEventType.FAMILY_MEMBER_JOINED, listOf(owner, member), familyId, newbie),
			),
			published(),
		)
		// 커밋 뒤에만 Kafka 로 옮기는 리스너까지 이어지는지 본다.
		verify(notificationEventRelay).relay(familyEvent(NotificationEventType.FAMILY_MEMBER_JOINED, listOf(owner, member), familyId, newbie))
	}

	private fun invite(familyId: UUID): UUID {
		`when`(accountClient.getNicknames(setOf(newbie))).thenReturn(mapOf(newbie to "막내"))
		val response = mockMvc.post("/api/v1/family/$familyId/invitation") {
			header("Authorization", "Bearer ${accessToken(owner)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"inviteeAccountId":"$newbie"}"""
		}.andReturn().response.contentAsString
		return UUID.fromString(objectMapper.readTree(response).get("data").get("familyInvitationId").asString())
	}

	private fun invitedEvent(familyId: UUID, invitationId: UUID) = NotificationEvent(
		NotificationEventType.FAMILY_INVITED,
		NotificationEventData(
			recipientAccountIds = listOf(newbie),
			familyId = familyId,
			targetType = NotificationTargetType.FAMILY_INVITATION,
			targetId = invitationId,
			actorAccountId = owner,
		),
	)

	@Test
	fun `초대하면 받는 사람에게 알리고 거절하면 초대한 사람에게 알린다`() {
		val familyId = createFamily()
		val invitationId = invite(familyId)

		mockMvc.post("/api/v1/family-invitation/$invitationId/decline") { header("Authorization", "Bearer ${accessToken(newbie)}") }
			.andExpect { status { isNoContent() } }

		assertEquals(
			listOf(invitedEvent(familyId, invitationId), familyEvent(NotificationEventType.FAMILY_INVITATION_DECLINED, listOf(owner), familyId, newbie)),
			published(),
		)
	}

	@Test
	fun `초대한 사람이 가족을 떠났으면 거절을 알리지 않는다`() {
		val familyId = createFamily()
		val invitationId = invite(familyId)
		val inviter = requireNotNull(familyMemberRepository.findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, owner))
		inviter.delete()
		familyMemberRepository.save(inviter)

		mockMvc.post("/api/v1/family-invitation/$invitationId/decline") { header("Authorization", "Bearer ${accessToken(newbie)}") }
			.andExpect { status { isNoContent() } }

		assertEquals(listOf(invitedEvent(familyId, invitationId)), published())
		assertEquals(null, familyInvitationRepository.findByFamilyInvitationIdAndIsDeletedFalse(invitationId))
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

	private fun saveMember(familyId: UUID, accountId: UUID) =
		requireNotNull(familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = accountId, role = FamilyRole.MEMBER)).familyMemberId)

	@Test
	fun `스스로 나가면 남은 구성원에게 알린다`() {
		val familyId = createFamily()
		saveMember(familyId, member)
		saveMember(familyId, newbie)

		mockMvc.post("/api/v1/family/$familyId/leave") { header("Authorization", "Bearer ${accessToken(newbie)}") }

		assertEquals(listOf(familyEvent(NotificationEventType.FAMILY_MEMBER_LEFT, listOf(owner, member), familyId, newbie)), published())
	}

	@Test
	fun `혼자 남은 OWNER가 나가면 알림이 없다`() {
		val familyId = createFamily()

		mockMvc.post("/api/v1/family/$familyId/leave") { header("Authorization", "Bearer ${accessToken(owner)}") }
			.andExpect { status { isNoContent() } }

		assertEquals(emptyList(), published())
	}

	@Test
	fun `내보내면 내보낸 사람에게만 알리고 가족 화면으로 이동하지 않는다`() {
		val familyId = createFamily()
		val familyMemberId = saveMember(familyId, member)
		saveMember(familyId, newbie)

		mockMvc.delete("/api/v1/family/$familyId/member/$familyMemberId") { header("Authorization", "Bearer ${accessToken(owner)}") }

		assertEquals(
			listOf(
				NotificationEvent(
					NotificationEventType.FAMILY_MEMBER_REMOVED,
					NotificationEventData(recipientAccountIds = listOf(member), familyId = familyId, actorAccountId = owner),
				),
			),
			published(),
		)
	}

	@Test
	fun `소유권을 넘기면 새 OWNER에게 알린다`() {
		val familyId = createFamily()
		val familyMemberId = saveMember(familyId, member)

		mockMvc.post("/api/v1/family/$familyId/member/$familyMemberId/transfer-ownership") {
			header("Authorization", "Bearer ${accessToken(owner)}")
		}

		assertEquals(listOf(familyEvent(NotificationEventType.FAMILY_OWNERSHIP_TRANSFERRED, listOf(member), familyId, owner)), published())
	}
}
