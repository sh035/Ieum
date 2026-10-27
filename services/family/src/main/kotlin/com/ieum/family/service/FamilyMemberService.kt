package com.ieum.family.service

import com.ieum.family.controller.dto.response.FamilyMemberResponse
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

		return members.map {
			FamilyMemberResponse(
				familyMemberId = requireNotNull(it.familyMemberId),
				accountId = it.accountId,
				role = it.role,
				joinedAt = it.createdAt,
			)
		}
	}
}
