package com.ieum.family.service

import com.ieum.events.FamilyEventData
import com.ieum.events.FamilyEventType
import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventPublisher
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.family.controller.dto.response.CreateFamilyResponse
import com.ieum.family.controller.dto.response.FamilyInfoResponse
import com.ieum.family.controller.dto.response.FamilyResponse
import com.ieum.family.controller.dto.response.FamilySummaryResponse
import com.ieum.family.controller.dto.response.LeaveAllFamiliesResult
import com.ieum.family.controller.dto.response.WithdrawalCheckResponse
import com.ieum.family.domain.entity.Family
import com.ieum.family.domain.entity.FamilyMember
import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.exception.FamilyErrorCode
import com.ieum.family.repository.FamilyInvitationRepository
import com.ieum.family.repository.FamilyMemberRepository
import com.ieum.family.repository.FamilyRepository
import com.ieum.grpc.client.account.AccountClient
import com.ieum.web.exception.IeumException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FamilyService(
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
	private val familyInvitationRepository: FamilyInvitationRepository,
	private val notificationEventPublisher: NotificationEventPublisher,
	private val familyEventPublisher: FamilyEventPublisher,
	private val accountClient: AccountClient,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	@Transactional
	fun createFamily(accountId: String, name: String): CreateFamilyResponse {
		val me = UUID.fromString(accountId)
		val nickname = accountClient.accountNickname(me)
		val familyId = requireNotNull(familyRepository.save(Family(name = name)).familyId)

		familyMemberRepository.save(
			FamilyMember(
				familyId = familyId,
				accountId = me,
				role = FamilyRole.OWNER,
				nickname = nickname,
			),
		)

		log.debug("Family created. familyId={}, accountId={}", familyId, accountId)

		return CreateFamilyResponse(familyId)
	}

	// 가입한 순서대로 반환한다.
	@Transactional(readOnly = true)
	fun getMyFamilies(accountId: String): List<FamilyResponse> {
		val members = familyMemberRepository.findAllByAccountIdAndIsDeletedFalseOrderByCreatedAt(UUID.fromString(accountId))
		val families = familyRepository.findAllByFamilyIdInAndIsDeletedFalse(members.map { it.familyId })
			.associateBy { requireNotNull(it.familyId) }

		return members.mapNotNull { member ->
			families[member.familyId]?.let { FamilyResponse(familyId = member.familyId, name = it.name, role = member.role) }
		}
	}

	// OWNER 는 다른 구성원이 있으면 소유권을 먼저 넘겨야 한다. 혼자 남은 OWNER 가 나가면 가족을 삭제한다.
	@Transactional
	fun leave(accountId: String, familyId: UUID) {
		val me = UUID.fromString(accountId)
		val family = familyRepository.findWithLockByFamilyIdAndIsDeletedFalse(familyId)
			?: throw IeumException(FamilyErrorCode.FAMILY_NOT_FOUND)
		val members = familyMemberRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId)
		val mine = members.find { it.accountId == me } ?: throw IeumException(FamilyErrorCode.FAMILY_NOT_FOUND)
		val others = members - mine
		if (mine.role == FamilyRole.OWNER && others.isNotEmpty()) {
			throw IeumException(FamilyErrorCode.OWNERSHIP_TRANSFER_REQUIRED)
		}

		mine.delete()
		if (others.isEmpty()) {
			family.delete()
			familyInvitationRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId).forEach { it.delete() }
			familyEventPublisher.publish(FamilyEventType.FAMILY_DELETED, FamilyEventData(familyId = familyId))
			log.debug("Family deleted. familyId={}, accountId={}", familyId, accountId)
			return
		}

		familyEventPublisher.publish(FamilyEventType.FAMILY_MEMBER_LEFT, FamilyEventData(familyId = familyId, accountId = me))
		notificationEventPublisher.publish(
			NotificationEventType.FAMILY_MEMBER_LEFT,
			NotificationEventData(
				recipientAccountIds = others.map { it.accountId },
				familyId = familyId,
				targetType = NotificationTargetType.FAMILY,
				targetId = familyId,
				actorAccountId = me,
			),
		)
	}

	// 탈퇴 전 확인이다. 서비스 간 호출용이라 요청자 권한은 보지 않는다.
	@Transactional(readOnly = true)
	fun checkWithdrawal(accountId: UUID): WithdrawalCheckResponse = withdrawalCheck(accountId)

	// 탈퇴하는 계정을 모든 가족에서 내보낸다. 확인과 나가기 사이에 누가 들어오거나 나가지 않게 내 가족을 모두 잠근 뒤 다시 확인한다.
	@Transactional
	fun leaveAllFamilies(accountId: UUID, deleteSoloFamilies: Boolean): LeaveAllFamiliesResult {
		// 여러 가족을 잠그므로 순서를 맞춰 다른 탈퇴와 교착되지 않게 한다.
		familyMemberRepository.findAllByAccountIdAndIsDeletedFalseOrderByCreatedAt(accountId)
			.map { it.familyId }
			.sorted()
			.forEach { familyRepository.findWithLockByFamilyIdAndIsDeletedFalse(it) }

		val check = withdrawalCheck(accountId)
		if (check.transferRequiredFamilies.isNotEmpty() || (check.soloFamilies.isNotEmpty() && !deleteSoloFamilies)) {
			return LeaveAllFamiliesResult(left = false, check = check)
		}

		familyMemberRepository.findAllByAccountIdAndIsDeletedFalseOrderByCreatedAt(accountId).forEach { leave(accountId.toString(), it.familyId) }
		familyInvitationRepository.findAllByInviteeAccountIdAndIsDeletedFalseOrderByCreatedAt(accountId).forEach { it.delete() }
		log.debug("Left all families for withdrawal. accountId={}", accountId)

		return LeaveAllFamiliesResult(left = true, check = check)
	}

	private fun withdrawalCheck(accountId: UUID): WithdrawalCheckResponse {
		val members = familyMemberRepository.findAllByAccountIdAndIsDeletedFalseOrderByCreatedAt(accountId)
		val families = familyRepository.findAllByFamilyIdInAndIsDeletedFalse(members.map { it.familyId }).associateBy { requireNotNull(it.familyId) }
		val transferRequired = mutableListOf<FamilySummaryResponse>()
		val solo = mutableListOf<FamilySummaryResponse>()

		members.forEach { member ->
			val family = families[member.familyId] ?: return@forEach
			val summary = FamilySummaryResponse(familyId = member.familyId, name = family.name)
			when {
				familyMemberRepository.countByFamilyIdAndIsDeletedFalse(member.familyId) == 1L -> solo += summary
				member.role == FamilyRole.OWNER -> transferRequired += summary
			}
		}

		return WithdrawalCheckResponse(transferRequiredFamilies = transferRequired, soloFamilies = solo)
	}

	// 서비스 간 호출용이라 요청자 권한은 보지 않는다.
	@Transactional(readOnly = true)
	fun getFamilyInfo(familyId: UUID): FamilyInfoResponse {
		val family = familyRepository.findByFamilyIdAndIsDeletedFalse(familyId)
			?: throw IeumException(FamilyErrorCode.FAMILY_NOT_FOUND)
		val members = familyMemberRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId)

		return FamilyInfoResponse(name = family.name, members = members.associate { it.accountId to it.role })
	}
}
