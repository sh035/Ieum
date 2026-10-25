package com.ieum.family.service

import com.ieum.family.controller.dto.response.CreateFamilyResponse
import com.ieum.family.controller.dto.response.FamilyResponse
import com.ieum.family.domain.entity.Family
import com.ieum.family.domain.entity.FamilyMember
import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.repository.FamilyMemberRepository
import com.ieum.family.repository.FamilyRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FamilyService(
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	@Transactional
	fun createFamily(accountId: String, name: String): CreateFamilyResponse {
		val familyId = requireNotNull(familyRepository.save(Family(name = name)).familyId)

		familyMemberRepository.save(
			FamilyMember(
				familyId = familyId,
				accountId = UUID.fromString(accountId),
				role = FamilyRole.OWNER,
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
}
