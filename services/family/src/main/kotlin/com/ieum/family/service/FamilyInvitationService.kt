package com.ieum.family.service

import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventPublisher
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.family.controller.dto.response.FamilyInvitationResponse
import com.ieum.family.controller.dto.response.FamilyResponse
import com.ieum.family.controller.dto.response.InvitationCandidateResponse
import com.ieum.family.controller.dto.response.ReceivedFamilyInvitationResponse
import com.ieum.family.domain.entity.FamilyInvitation
import com.ieum.family.domain.entity.FamilyMember
import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.exception.FamilyErrorCode
import com.ieum.family.repository.FamilyInvitationRepository
import com.ieum.family.repository.FamilyMemberRepository
import com.ieum.family.repository.FamilyRepository
import com.ieum.grpc.client.account.AccountClient
import com.ieum.web.exception.IeumException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@Service
class FamilyInvitationService(
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
	private val familyInvitationRepository: FamilyInvitationRepository,
	private val accountClient: AccountClient,
	private val notificationEventPublisher: NotificationEventPublisher,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// 계정이 없는지 이미 구성원인지 구분하지 않고 같은 404 로 답한다.
	@Transactional(readOnly = true)
	fun findCandidate(accountId: String, familyId: UUID, email: String): InvitationCandidateResponse {
		checkOwner(accountId, familyId)

		val profile = accountClient.findByEmail(email.trim())
			?.takeUnless { familyMemberRepository.existsByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, it.accountId) }
			?: throw IeumException(FamilyErrorCode.INVITEE_NOT_FOUND)

		return InvitationCandidateResponse(accountId = profile.accountId, nickname = profile.nickname)
	}

	// 같은 사람에게 살아 있는 초대가 있으면 새로 만들지 않는다. 만료된 초대는 지우고 다시 만든다.
	@Transactional
	fun createInvitation(accountId: String, familyId: UUID, inviteeAccountId: UUID): FamilyInvitationResponse {
		// account 가 느려도 같은 가족의 다른 요청이 기다리지 않게 잠그기 전에 계정을 확인한다.
		val nickname = accountClient.getNicknames(setOf(inviteeAccountId))[inviteeAccountId]
			?: throw IeumException(FamilyErrorCode.INVITEE_NOT_FOUND)
		familyRepository.findWithLockByFamilyIdAndIsDeletedFalse(familyId) ?: throw IeumException(FamilyErrorCode.FAMILY_NOT_FOUND)
		val owner = checkOwner(accountId, familyId)

		if (familyMemberRepository.existsByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, inviteeAccountId)) {
			throw IeumException(FamilyErrorCode.ALREADY_FAMILY_MEMBER)
		}
		familyInvitationRepository.findByFamilyIdAndInviteeAccountIdAndIsDeletedFalse(familyId, inviteeAccountId)?.let {
			if (!it.isExpired()) {
				throw IeumException(FamilyErrorCode.INVITATION_ALREADY_EXISTS)
			}
			it.delete()
			// 같은 사람에게 살아 있는 초대 하나 unique index 때문에 insert 전에 반영한다.
			familyInvitationRepository.flush()
		}

		val invitation = familyInvitationRepository.save(
			FamilyInvitation(
				familyId = familyId,
				inviterAccountId = owner.accountId,
				inviteeAccountId = inviteeAccountId,
				expiresAt = LocalDateTime.now().plus(INVITATION_TTL),
			),
		)

		notificationEventPublisher.publish(
			NotificationEventType.FAMILY_INVITED,
			NotificationEventData(
				recipientAccountIds = listOf(inviteeAccountId),
				familyId = familyId,
				targetType = NotificationTargetType.FAMILY_INVITATION,
				targetId = invitation.familyInvitationId,
				actorAccountId = owner.accountId,
			),
		)

		log.debug("Family invitation created. familyId={}, accountId={}", familyId, accountId)

		return invitation.toResponse(nickname)
	}

	@Transactional(readOnly = true)
	fun getInvitations(accountId: String, familyId: UUID): List<FamilyInvitationResponse> {
		checkOwner(accountId, familyId)

		val invitations = familyInvitationRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId).filterNot { it.isExpired() }
		val nicknames = if (invitations.isEmpty()) emptyMap() else accountClient.getNicknames(invitations.map { it.inviteeAccountId }.toSet())

		return invitations.map { it.toResponse(nicknames[it.inviteeAccountId]) }
	}

	@Transactional
	fun cancelInvitation(accountId: String, familyId: UUID, familyInvitationId: UUID) {
		familyRepository.findWithLockByFamilyIdAndIsDeletedFalse(familyId) ?: throw IeumException(FamilyErrorCode.FAMILY_NOT_FOUND)
		checkOwner(accountId, familyId)

		val invitation = familyInvitationRepository.findByFamilyInvitationIdAndFamilyIdAndIsDeletedFalse(familyInvitationId, familyId)
			?: throw IeumException(FamilyErrorCode.INVITATION_NOT_FOUND)
		invitation.delete()
	}

	@Transactional(readOnly = true)
	fun getReceivedInvitations(accountId: String): List<ReceivedFamilyInvitationResponse> {
		val invitations = familyInvitationRepository.findAllByInviteeAccountIdAndIsDeletedFalseOrderByCreatedAt(UUID.fromString(accountId))
			.filterNot { it.isExpired() }
		if (invitations.isEmpty()) {
			return emptyList()
		}
		val families = familyRepository.findAllByFamilyIdInAndIsDeletedFalse(invitations.map { it.familyId }).associateBy { requireNotNull(it.familyId) }
		val nicknames = accountClient.getNicknames(invitations.map { it.inviterAccountId }.toSet())

		return invitations.mapNotNull { invitation ->
			families[invitation.familyId]?.let { family ->
				ReceivedFamilyInvitationResponse(
					familyInvitationId = requireNotNull(invitation.familyInvitationId),
					familyId = invitation.familyId,
					familyName = family.name,
					inviterAccountId = invitation.inviterAccountId,
					inviterNickname = nicknames[invitation.inviterAccountId],
					expiresAt = invitation.expiresAt,
				)
			}
		}
	}

	@Transactional
	fun acceptInvitation(accountId: String, familyInvitationId: UUID): FamilyResponse {
		val me = UUID.fromString(accountId)
		val familyId = findReceivedInvitation(me, familyInvitationId).familyId
		// 혼자 남은 OWNER 가 동시에 나가 가족이 삭제되면 여기서 걸러낸다.
		val family = familyRepository.findWithLockByFamilyIdAndIsDeletedFalse(familyId)
			?: throw IeumException(FamilyErrorCode.INVITATION_NOT_FOUND)
		// 잠그기 전에 취소나 거절이 끝났을 수 있어 잠근 뒤 다시 확인한다.
		val invitation = findReceivedInvitation(me, familyInvitationId)

		if (familyMemberRepository.existsByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, me)) {
			throw IeumException(FamilyErrorCode.ALREADY_FAMILY_MEMBER)
		}

		// 동시 수락은 exists 확인을 함께 통과하므로 unique index 위반으로 걸러낸다. 트랜잭션 안이라 바로 flush 해야 여기서 잡힌다.
		try {
			familyMemberRepository.saveAndFlush(FamilyMember(familyId = familyId, accountId = me, role = FamilyRole.MEMBER))
		} catch (e: DataIntegrityViolationException) {
			throw IeumException(FamilyErrorCode.ALREADY_FAMILY_MEMBER)
		}
		invitation.delete()

		notificationEventPublisher.publish(
			NotificationEventType.FAMILY_MEMBER_JOINED,
			NotificationEventData(
				recipientAccountIds = familyMemberRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId).map { it.accountId },
				familyId = familyId,
				targetType = NotificationTargetType.FAMILY,
				targetId = familyId,
				actorAccountId = me,
			),
		)

		log.debug("Family invitation accepted. familyId={}, accountId={}", familyId, accountId)

		return FamilyResponse(familyId = familyId, name = family.name, role = FamilyRole.MEMBER)
	}

	@Transactional
	fun declineInvitation(accountId: String, familyInvitationId: UUID) {
		val me = UUID.fromString(accountId)
		val familyId = findReceivedInvitation(me, familyInvitationId).familyId
		familyRepository.findWithLockByFamilyIdAndIsDeletedFalse(familyId) ?: throw IeumException(FamilyErrorCode.INVITATION_NOT_FOUND)
		val invitation = findReceivedInvitation(me, familyInvitationId)
		invitation.delete()

		notificationEventPublisher.publish(
			NotificationEventType.FAMILY_INVITATION_DECLINED,
			NotificationEventData(
				recipientAccountIds = listOf(invitation.inviterAccountId),
				familyId = invitation.familyId,
				targetType = NotificationTargetType.FAMILY,
				targetId = invitation.familyId,
				actorAccountId = me,
			),
		)
	}

	// 구성원이 아니면 가족의 존재 자체를 숨긴다.
	private fun checkOwner(accountId: String, familyId: UUID): FamilyMember {
		val member = familyMemberRepository.findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, UUID.fromString(accountId))
			?: throw IeumException(FamilyErrorCode.FAMILY_NOT_FOUND)

		if (member.role != FamilyRole.OWNER) {
			throw IeumException(FamilyErrorCode.FAMILY_ACCESS_DENIED)
		}
		return member
	}

	// 남의 초대는 존재 자체를 숨긴다.
	private fun findReceivedInvitation(me: UUID, familyInvitationId: UUID): FamilyInvitation =
		familyInvitationRepository.findByFamilyInvitationIdAndIsDeletedFalse(familyInvitationId)
			?.takeIf { it.inviteeAccountId == me && !it.isExpired() }
			?: throw IeumException(FamilyErrorCode.INVITATION_NOT_FOUND)

	private fun FamilyInvitation.toResponse(inviteeNickname: String?) = FamilyInvitationResponse(
		familyInvitationId = requireNotNull(familyInvitationId),
		inviteeAccountId = inviteeAccountId,
		inviteeNickname = inviteeNickname,
		expiresAt = expiresAt,
	)

	companion object {
		private val INVITATION_TTL = Duration.ofHours(1)
	}
}
