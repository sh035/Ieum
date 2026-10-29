package com.ieum.family.service

import com.ieum.family.controller.dto.request.UpdateFamilyMemberRoleRequest
import com.ieum.family.controller.dto.response.FamilyMemberResponse
import com.ieum.family.controller.dto.response.FamilyMemberRoleResponse
import com.ieum.family.domain.entity.FamilyMember
import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.exception.FamilyErrorCode
import com.ieum.family.repository.FamilyMemberRepository
import com.ieum.web.exception.IeumException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FamilyMemberService(
	private val familyMemberRepository: FamilyMemberRepository,
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
	fun updateRole(
		accountId: String,
		familyId: UUID,
		familyMemberId: UUID,
		request: UpdateFamilyMemberRoleRequest,
	): FamilyMemberResponse {
		val requester = familyMemberRepository.findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, UUID.fromString(accountId))
			?: throw IeumException(FamilyErrorCode.FAMILY_NOT_FOUND)
		if (requester.role != FamilyRole.OWNER) {
			throw IeumException(FamilyErrorCode.FAMILY_ACCESS_DENIED)
		}

		val member = familyMemberRepository.findByFamilyMemberIdAndFamilyIdAndIsDeletedFalse(familyMemberId, familyId)
			?: throw IeumException(FamilyErrorCode.FAMILY_MEMBER_NOT_FOUND)
		if (request.role == FamilyRole.OWNER || member.role == FamilyRole.OWNER) {
			throw IeumException(FamilyErrorCode.INVALID_ROLE_CHANGE)
		}

		member.role = request.role
		return familyMemberRepository.save(member).toResponse()
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

	private fun FamilyMember.toResponse() = FamilyMemberResponse(
		familyMemberId = requireNotNull(familyMemberId),
		accountId = accountId,
		role = role,
		joinedAt = createdAt,
	)
}
