package com.ieum.family

import com.ieum.events.FamilyEventData
import com.ieum.events.FamilyEventType
import com.ieum.events.IeumEvent
import com.ieum.events.Topics
import com.ieum.family.domain.entity.Family
import com.ieum.family.domain.entity.FamilyMember
import com.ieum.family.domain.entity.OutboxEvent
import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.repository.FamilyInvitationRepository
import com.ieum.family.repository.FamilyMemberRepository
import com.ieum.family.repository.FamilyRepository
import com.ieum.family.repository.OutboxEventRepository
import com.ieum.family.scheduler.OutboxScheduler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
class FamilyOutboxTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val objectMapper: ObjectMapper,
	private val clock: Clock,
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
	private val familyInvitationRepository: FamilyInvitationRepository,
	private val outboxEventRepository: OutboxEventRepository,
	private val outboxScheduler: OutboxScheduler,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var kafkaTemplate: KafkaTemplate<String, String>

	@Suppress("UNCHECKED_CAST")
	private val sendResult = mock(SendResult::class.java) as SendResult<String, String>
	private val owner = UUID.randomUUID()
	private val member = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		outboxEventRepository.deleteAll()
		familyInvitationRepository.deleteAll()
		familyMemberRepository.deleteAll()
		familyRepository.deleteAll()
	}

	private fun createFamily(vararg members: UUID): UUID {
		val familyId = requireNotNull(familyRepository.save(Family(name = "우리집")).familyId)
		familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = owner, role = FamilyRole.OWNER))
		members.forEach { familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = it, role = FamilyRole.MEMBER)) }
		return familyId
	}

	private fun stored() = outboxEventRepository.findAll().map {
		Triple(it.eventType, it.familyId, objectMapper.readValue(it.payload, FamilyEventData::class.java))
	}

	private fun event(type: FamilyEventType, familyId: UUID, createdAt: LocalDateTime) = outboxEventRepository.save(
		OutboxEvent(
			eventType = type,
			familyId = familyId,
			payload = objectMapper.writeValueAsString(FamilyEventData(familyId = familyId)),
			createdAt = createdAt,
		),
	)

	// 시각은 DB 가 자른 정밀도까지 같아야 해서 저장된 행에서 가져오고, 종류와 내용은 기대값을 직접 쓴다.
	private fun envelope(event: OutboxEvent, type: String, familyId: UUID) = objectMapper.writeValueAsString(
		IeumEvent(
			eventId = requireNotNull(event.outboxEventId),
			eventType = type,
			createdAt = reload(event).createdAt,
			data = FamilyEventData(familyId = familyId),
		),
	)

	private fun reload(event: OutboxEvent) = outboxEventRepository.findById(requireNotNull(event.outboxEventId)).get()

	@Test
	fun `구성원이 나가면 떠남 이벤트를 쌓는다`() {
		val familyId = createFamily(member)

		mockMvc.post("/api/v1/family/$familyId/leave") { header("Authorization", "Bearer ${accessToken(member)}") }
			.andExpect { status { isNoContent() } }

		assertEquals(listOf(Triple(FamilyEventType.FAMILY_MEMBER_LEFT, familyId, FamilyEventData(familyId, member))), stored())
	}

	@Test
	fun `내보내도 떠남 이벤트를 쌓는다`() {
		val familyId = createFamily(member)
		val familyMemberId = familyMemberRepository.findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, member)?.familyMemberId

		mockMvc.delete("/api/v1/family/$familyId/member/$familyMemberId") { header("Authorization", "Bearer ${accessToken(owner)}") }
			.andExpect { status { isNoContent() } }

		assertEquals(listOf(Triple(FamilyEventType.FAMILY_MEMBER_LEFT, familyId, FamilyEventData(familyId, member))), stored())
	}

	@Test
	fun `혼자 남은 OWNER가 나가면 가족 삭제 이벤트를 쌓는다`() {
		val familyId = createFamily()

		mockMvc.post("/api/v1/family/$familyId/leave") { header("Authorization", "Bearer ${accessToken(owner)}") }
			.andExpect { status { isNoContent() } }

		assertEquals(listOf(Triple(FamilyEventType.FAMILY_DELETED, familyId, FamilyEventData(familyId))), stored())
	}

	@Test
	fun `나갈 수 없으면 이벤트를 쌓지 않는다`() {
		val familyId = createFamily(member)

		mockMvc.post("/api/v1/family/$familyId/leave") { header("Authorization", "Bearer ${accessToken(owner)}") }
			.andExpect { status { isConflict() } }

		assertEquals(emptyList(), stored())
	}

	@Test
	fun `안 보낸 이벤트를 오래된 순으로 보내고 보낸 시각을 남긴다`() {
		val familyId = UUID.randomUUID()
		val now = LocalDateTime.now(clock)
		val later = event(FamilyEventType.FAMILY_DELETED, familyId, now.minusSeconds(1))
		val earlier = event(FamilyEventType.FAMILY_MEMBER_LEFT, familyId, now.minusSeconds(2))
		val sent = event(FamilyEventType.FAMILY_MEMBER_LEFT, familyId, now.minusSeconds(3)).apply { sentAt = now }
		outboxEventRepository.save(sent)
		val earlierEnvelope = envelope(earlier, "FAMILY_MEMBER_LEFT", familyId)
		val laterEnvelope = envelope(later, "FAMILY_DELETED", familyId)
		listOf(earlierEnvelope, laterEnvelope).forEach {
			`when`(kafkaTemplate.send(Topics.FAMILY, familyId.toString(), it)).thenReturn(CompletableFuture.completedFuture(sendResult))
		}

		outboxScheduler.relay()

		val order = inOrder(kafkaTemplate)
		order.verify(kafkaTemplate).send(Topics.FAMILY, familyId.toString(), earlierEnvelope)
		order.verify(kafkaTemplate).send(Topics.FAMILY, familyId.toString(), laterEnvelope)
		verifyNoMoreInteractions(kafkaTemplate)
		assertNotNull(reload(earlier).sentAt)
		assertNotNull(reload(later).sentAt)
	}

	@Test
	fun `보내기에 실패하면 거기서 멈추고 다음 주기에 다시 보낸다`() {
		val familyId = UUID.randomUUID()
		val now = LocalDateTime.now(clock)
		val first = event(FamilyEventType.FAMILY_MEMBER_LEFT, familyId, now.minusSeconds(2))
		val second = event(FamilyEventType.FAMILY_DELETED, familyId, now.minusSeconds(1))
		val firstEnvelope = envelope(first, "FAMILY_MEMBER_LEFT", familyId)
		val secondEnvelope = envelope(second, "FAMILY_DELETED", familyId)
		`when`(kafkaTemplate.send(Topics.FAMILY, familyId.toString(), firstEnvelope))
			.thenReturn(CompletableFuture.failedFuture(IllegalStateException("broker down")))
			.thenReturn(CompletableFuture.completedFuture(sendResult))
		`when`(kafkaTemplate.send(Topics.FAMILY, familyId.toString(), secondEnvelope)).thenReturn(CompletableFuture.completedFuture(sendResult))

		outboxScheduler.relay()

		verify(kafkaTemplate).send(Topics.FAMILY, familyId.toString(), firstEnvelope)
		verifyNoMoreInteractions(kafkaTemplate)
		assertNull(reload(first).sentAt)
		assertNull(reload(second).sentAt)

		outboxScheduler.relay()

		val order = inOrder(kafkaTemplate)
		order.verify(kafkaTemplate, times(2)).send(Topics.FAMILY, familyId.toString(), firstEnvelope)
		order.verify(kafkaTemplate).send(Topics.FAMILY, familyId.toString(), secondEnvelope)
		assertNotNull(reload(first).sentAt)
		assertNotNull(reload(second).sentAt)
	}

	@Test
	fun `payload 는 클래스로 다시 읽지 않고 저장된 그대로 싣는다`() {
		val familyId = UUID.randomUUID()
		val payload = """{"familyId":"$familyId","removedBy":"owner"}"""
		val event = reload(
			outboxEventRepository.save(
				OutboxEvent(eventType = FamilyEventType.FAMILY_MEMBER_LEFT, familyId = familyId, payload = payload, createdAt = LocalDateTime.now(clock)),
			),
		)
		val expected = objectMapper.writeValueAsString(
			IeumEvent(requireNotNull(event.outboxEventId), "FAMILY_MEMBER_LEFT", event.createdAt, objectMapper.readTree(payload)),
		)
		`when`(kafkaTemplate.send(Topics.FAMILY, familyId.toString(), expected)).thenReturn(CompletableFuture.completedFuture(sendResult))

		outboxScheduler.relay()

		verify(kafkaTemplate).send(Topics.FAMILY, familyId.toString(), expected)
		verifyNoMoreInteractions(kafkaTemplate)
		assertNotNull(reload(event).sentAt)
	}

	@Test
	fun `보낸 지 7일이 지난 이벤트만 지운다`() {
		val familyId = UUID.randomUUID()
		val now = LocalDateTime.now(clock)
		val old = event(FamilyEventType.FAMILY_MEMBER_LEFT, familyId, now.minusDays(9)).apply { sentAt = now.minusDays(8) }
		val recent = event(FamilyEventType.FAMILY_MEMBER_LEFT, familyId, now.minusDays(7)).apply { sentAt = now.minusDays(6) }
		val unsent = event(FamilyEventType.FAMILY_DELETED, familyId, now.minusDays(9))
		outboxEventRepository.saveAll(listOf(old, recent))

		outboxScheduler.cleanup()

		assertEquals(setOf(recent.outboxEventId, unsent.outboxEventId), outboxEventRepository.findAll().map { it.outboxEventId }.toSet())
	}
}
