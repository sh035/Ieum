package com.ieum.family.service

import com.ieum.events.FamilyEventData
import com.ieum.events.FamilyEventType
import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventPublisher
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.family.controller.dto.request.UpdateFamilyMemberRoleRequest
import com.ieum.family.controller.dto.response.FamilyMemberResponse
import com.ieum.family.controller.dto.response.FamilyMemberRoleResponse
import com.ieum.family.domain.entity.FamilyMember
import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.exception.FamilyErrorCode
import com.ieum.family.repository.FamilyMemberRepository
import com.ieum.family.repository.FamilyRepository
import com.ieum.web.exception.IeumException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FamilyMemberService(
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
	private val notificationEventPublisher: NotificationEventPublisher,
	private val familyEventPublisher: FamilyEventPublisher,
) {

	// 가입한 순서대로 반환한다. 구성원이 아니면 가족의 존재 자체를 숨긴다.
	fun getMembers(accountId: String, familyId: UUID): List<FamilyMemberResponse> {
		val members = familyMemberRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId)

		if (members.none { it.accountId == UUID.fromString(accountId) }) {
			throw IeumException(FamilyErrorCode.FAMILY_NOT_FOUND)
		}

		return members.map { it.toResponse() }
	}

	// OWNER 가 구성원을 MEMBER <-> MANAGER 로 바꾼다. OWNER 역할은 이 API 로 주거나 뺏을 수 없다.
	@Transactional
	fun updateRole(
		accountId: String,
		familyId: UUID,
		familyMemberId: UUID,
		request: UpdateFamilyMemberRoleRequest,
	): FamilyMemberResponse {
		val requester = findOwner(accountId, familyId)

		val member = familyMemberRepository.findByFamilyMemberIdAndFamilyIdAndIsDeletedFalse(familyMemberId, familyId)
			?: throw IeumException(FamilyErrorCode.FAMILY_MEMBER_NOT_FOUND)
		if (request.role == FamilyRole.OWNER || member.role == FamilyRole.OWNER) {
			throw IeumException(FamilyErrorCode.INVALID_ROLE_CHANGE)
		}

		val changed = member.role != request.role
		member.role = request.role
		val saved = familyMemberRepository.save(member)

		if (changed) {
			notificationEventPublisher.publish(
				NotificationEventType.FAMILY_ROLE_CHANGED,
				NotificationEventData(
					recipientAccountIds = listOf(member.accountId),
					familyId = familyId,
					targetType = NotificationTargetType.FAMILY,
					targetId = familyId,
					actorAccountId = requester.accountId,
					role = request.role.name,
				),
			)
		}
		return saved.toResponse()
	}

	// OWNER 만 내보낼 수 있고 OWNER 는 내보낼 수 없다. 내보낸 사람은 가족 화면에 들어갈 수 없어 알림에 이동 대상을 넣지 않는다.
	@Transactional
	fun removeMember(accountId: String, familyId: UUID, familyMemberId: UUID) {
		val owner = findOwner(accountId, familyId)
		val member = familyMemberRepository.findByFamilyMemberIdAndFamilyIdAndIsDeletedFalse(familyMemberId, familyId)
			?: throw IeumException(FamilyErrorCode.FAMILY_MEMBER_NOT_FOUND)
		if (member.role == FamilyRole.OWNER) {
			throw IeumException(FamilyErrorCode.CANNOT_REMOVE_OWNER)
		}

		member.delete()

		familyEventPublisher.publish(FamilyEventType.FAMILY_MEMBER_LEFT, FamilyEventData(familyId = familyId, accountId = member.accountId))
		notificationEventPublisher.publish(
			NotificationEventType.FAMILY_MEMBER_REMOVED,
			NotificationEventData(recipientAccountIds = listOf(member.accountId), familyId = familyId, actorAccountId = owner.accountId),
		)
	}

	// OWNER 가 나가려면 앱이 이 API 로 넘긴 뒤 나가기를 부른다.
	@Transactional
	fun transferOwnership(accountId: String, familyId: UUID, familyMemberId: UUID) {
		val owner = findOwner(accountId, familyId)
		val member = familyMemberRepository.findByFamilyMemberIdAndFamilyIdAndIsDeletedFalse(familyMemberId, familyId)
			?: throw IeumException(FamilyErrorCode.FAMILY_MEMBER_NOT_FOUND)
		if (member.familyMemberId == owner.familyMemberId) {
			throw IeumException(FamilyErrorCode.INVALID_OWNERSHIP_TRANSFER)
		}

		owner.role = FamilyRole.MEMBER
		member.role = FamilyRole.OWNER

		notificationEventPublisher.publish(
			NotificationEventType.FAMILY_OWNERSHIP_TRANSFERRED,
			NotificationEventData(
				recipientAccountIds = listOf(member.accountId),
				familyId = familyId,
				targetType = NotificationTargetType.FAMILY,
				targetId = familyId,
				actorAccountId = owner.accountId,
			),
		)
	}

	fun getRole(familyId: UUID, accountId: UUID): FamilyMemberRoleResponse {
		val member = familyMemberRepository.findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, accountId)
			?: throw IeumException(FamilyErrorCode.FAMILY_MEMBER_NOT_FOUND)

		return FamilyMemberRoleResponse(role = member.role)
	}

	// 구성원이 아닌 계정은 결과에서 빠진다.
	fun getRoles(familyId: UUID, accountIds: Collection<UUID>): Map<UUID, FamilyRole> =
		familyMemberRepository.findAllByFamilyIdAndAccountIdInAndIsDeletedFalse(familyId, accountIds)
			.associate { it.accountId to it.role }

	// 가족을 먼저 잠가서 동시에 온 요청은 앞 요청이 끝난 뒤의 역할로 다시 확인한다.
	private fun findOwner(accountId: String, familyId: UUID): FamilyMember {
		familyRepository.findWithLockByFamilyIdAndIsDeletedFalse(familyId) ?: throw IeumException(FamilyErrorCode.FAMILY_NOT_FOUND)
		val requester = familyMemberRepository.findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, UUID.fromString(accountId))
			?: throw IeumException(FamilyErrorCode.FAMILY_NOT_FOUND)
		if (requester.role != FamilyRole.OWNER) {
			throw IeumException(FamilyErrorCode.FAMILY_ACCESS_DENIED)
		}
		return requester
	}

	private fun FamilyMember.toResponse() = FamilyMemberResponse(
		familyMemberId = requireNotNull(familyMemberId),
		accountId = accountId,
		role = role,
		joinedAt = createdAt,
	)
}
