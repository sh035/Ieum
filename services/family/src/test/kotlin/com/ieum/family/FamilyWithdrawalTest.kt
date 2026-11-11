package com.ieum.family

import com.ieum.events.FamilyEventType
import com.ieum.family.domain.entity.Family
import com.ieum.family.domain.entity.FamilyInvitation
import com.ieum.family.domain.entity.FamilyMember
import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.repository.FamilyInvitationRepository
import com.ieum.family.repository.FamilyMemberRepository
import com.ieum.family.repository.FamilyRepository
import com.ieum.family.repository.OutboxEventRepository
import com.ieum.proto.family.v1.CheckWithdrawalRequest
import com.ieum.proto.family.v1.FamilyInternalServiceGrpc
import com.ieum.proto.family.v1.LeaveAllFamiliesRequest
import com.ieum.proto.family.v1.WithdrawalCheck
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.grpc.client.GrpcChannelFactory
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureTestGrpcTransport
class FamilyWithdrawalTest @Autowired constructor(
	channels: GrpcChannelFactory,
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
	private val familyInvitationRepository: FamilyInvitationRepository,
	private val outboxEventRepository: OutboxEventRepository,
) : PostgresTestSupport() {

	private val stub = FamilyInternalServiceGrpc.newBlockingStub(channels.createChannel("family"))
	private val me = UUID.randomUUID()
	private val mom = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		outboxEventRepository.deleteAll()
		familyInvitationRepository.deleteAll()
		familyMemberRepository.deleteAll()
		familyRepository.deleteAll()
	}

	// members 의 첫 번째가 OWNER 다.
	private fun family(name: String, vararg members: UUID): UUID {
		val familyId = requireNotNull(familyRepository.save(Family(name = name)).familyId)
		members.forEachIndexed { index, accountId ->
			val role = if (index == 0) FamilyRole.OWNER else FamilyRole.MEMBER
			familyMemberRepository.save(FamilyMember(familyId = familyId, accountId = accountId, role = role, nickname = "구성원"))
		}
		return familyId
	}

	private fun check(): WithdrawalCheck =
		stub.checkWithdrawal(CheckWithdrawalRequest.newBuilder().setAccountId(me.toString()).build())

	private fun leaveAll(deleteSoloFamilies: Boolean) =
		stub.leaveAllFamilies(
			LeaveAllFamiliesRequest.newBuilder().setAccountId(me.toString()).setDeleteSoloFamilies(deleteSoloFamilies).build(),
		)

	private fun isMember(familyId: UUID) = familyMemberRepository.existsByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, me)

	@Test
	fun `확인은 소유권을 넘겨야 하는 가족과 혼자인 가족을 나눈다`() {
		val owned = family("우리집", me, mom)
		val solo = family("혼자", me)
		family("본가", mom, me)

		val result = check()

		assertEquals(listOf(owned.toString() to "우리집"), result.transferRequiredFamiliesList.map { it.familyId to it.name })
		assertEquals(listOf(solo.toString() to "혼자"), result.soloFamiliesList.map { it.familyId to it.name })
	}

	@Test
	fun `소유권을 넘겨야 하는 가족이 있으면 아무 가족에서도 나가지 않는다`() {
		val owned = family("우리집", me, mom)
		val joined = family("본가", mom, me)

		val response = leaveAll(deleteSoloFamilies = true)

		assertFalse(response.left)
		assertEquals(listOf(owned.toString()), response.check.transferRequiredFamiliesList.map { it.familyId })
		assertTrue(isMember(owned))
		assertTrue(isMember(joined))
		assertEquals(0, outboxEventRepository.count())
	}

	@Test
	fun `혼자인 가족 삭제에 동의하지 않으면 아무 가족에서도 나가지 않는다`() {
		val solo = family("혼자", me)
		val joined = family("본가", mom, me)

		val response = leaveAll(deleteSoloFamilies = false)

		assertFalse(response.left)
		assertEquals(listOf(solo.toString()), response.check.soloFamiliesList.map { it.familyId })
		assertTrue(isMember(solo))
		assertTrue(isMember(joined))
	}

	@Test
	fun `동의하면 모든 가족에서 나가고 혼자인 가족은 지우고 받은 초대도 지운다`() {
		val solo = family("혼자", me)
		val joined = family("본가", mom, me)
		val inviting = family("이웃", mom)
		familyInvitationRepository.save(
			FamilyInvitation(familyId = inviting, inviterAccountId = mom, inviteeAccountId = me, expiresAt = LocalDateTime.now().plusHours(1)),
		)

		val response = leaveAll(deleteSoloFamilies = true)

		assertTrue(response.left)
		assertFalse(isMember(solo))
		assertFalse(isMember(joined))
		assertTrue(familyRepository.findById(solo).get().isDeleted)
		assertFalse(familyRepository.findById(joined).get().isDeleted)
		assertTrue(familyInvitationRepository.findAllByInviteeAccountIdAndIsDeletedFalseOrderByCreatedAt(me).isEmpty())
		assertEquals(
			setOf(FamilyEventType.FAMILY_DELETED to solo, FamilyEventType.FAMILY_MEMBER_LEFT to joined),
			outboxEventRepository.findAll().map { it.eventType to it.familyId }.toSet(),
		)
	}

	@Test
	fun `속한 가족이 없으면 바로 나간 것으로 응답한다`() {
		val response = leaveAll(deleteSoloFamilies = false)

		assertTrue(response.left)
		assertTrue(response.check.transferRequiredFamiliesList.isEmpty())
		assertTrue(response.check.soloFamiliesList.isEmpty())
	}
}
