package com.ieum.family.service

import com.ieum.family.controller.dto.response.FamilyInvitationResponse
import com.ieum.family.controller.dto.response.FamilyResponse
import com.ieum.family.controller.dto.response.InvitedFamilyResponse
import com.ieum.family.domain.entity.Family
import com.ieum.family.domain.entity.FamilyInvitation
import com.ieum.family.domain.entity.FamilyMember
import com.ieum.family.domain.enums.FamilyRole
import com.ieum.family.exception.FamilyErrorCode
import com.ieum.family.repository.FamilyInvitationRepository
import com.ieum.family.repository.FamilyMemberRepository
import com.ieum.family.repository.FamilyRepository
import com.ieum.web.exception.IeumException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@Service
class FamilyInvitationService(
	private val familyRepository: FamilyRepository,
	private val familyMemberRepository: FamilyMemberRepository,
	private val familyInvitationRepository: FamilyInvitationRepository,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// 새 코드를 만들면 기존 코드는 폐기한다.
	@Transactional
	fun createInvitation(accountId: String, familyId: UUID): FamilyInvitationResponse {
		checkOwner(accountId, familyId)

		familyInvitationRepository.findByFamilyIdAndIsDeletedFalse(familyId)?.let {
			it.delete()
			// 가족당 유효 코드 1개 unique index 때문에 insert 전에 폐기를 먼저 반영한다.
			familyInvitationRepository.flush()
		}

		val invitation = familyInvitationRepository.save(
			FamilyInvitation(
				familyId = familyId,
				code = generateCode(),
				expiresAt = LocalDateTime.now().plus(INVITATION_TTL),
			),
		)

		log.debug("Family invitation created. familyId={}, accountId={}", familyId, accountId)

		return FamilyInvitationResponse(code = invitation.code, expiresAt = invitation.expiresAt)
	}

	@Transactional(readOnly = true)
	fun getInvitation(accountId: String, familyId: UUID): FamilyInvitationResponse {
		checkOwner(accountId, familyId)

		val invitation = familyInvitationRepository.findByFamilyIdAndIsDeletedFalse(familyId)
			?.takeUnless { it.isExpired() }
			?: throw IeumException(FamilyErrorCode.INVITATION_NOT_FOUND)

		return FamilyInvitationResponse(code = invitation.code, expiresAt = invitation.expiresAt)
	}

	@Transactional
	fun cancelInvitation(accountId: String, familyId: UUID) {
		checkOwner(accountId, familyId)

		familyInvitationRepository.findByFamilyIdAndIsDeletedFalse(familyId)?.delete()
	}

	@Transactional(readOnly = true)
	fun verifyInvitation(code: String): InvitedFamilyResponse {
		val family = findInvitedFamily(code)

		return InvitedFamilyResponse(familyId = requireNotNull(family.familyId), name = family.name)
	}

	fun acceptInvitation(accountId: String, code: String): FamilyResponse {
		val family = findInvitedFamily(code)
		val familyId = requireNotNull(family.familyId)

		if (familyMemberRepository.existsByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, UUID.fromString(accountId))) {
			throw IeumException(FamilyErrorCode.ALREADY_FAMILY_MEMBER)
		}

		// 동시 수락은 exists 확인을 함께 통과하므로 unique index 위반으로 걸러낸다.
		try {
			familyMemberRepository.save(
				FamilyMember(
					familyId = familyId,
					accountId = UUID.fromString(accountId),
					role = FamilyRole.MEMBER,
				),
			)
		} catch (e: DataIntegrityViolationException) {
			throw IeumException(FamilyErrorCode.ALREADY_FAMILY_MEMBER)
		}

		log.debug("Family invitation accepted. familyId={}, accountId={}", familyId, accountId)

		return FamilyResponse(familyId = familyId, name = family.name, role = FamilyRole.MEMBER)
	}

	// 구성원이 아니면 가족의 존재 자체를 숨긴다.
	private fun checkOwner(accountId: String, familyId: UUID) {
		val member = familyMemberRepository.findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId, UUID.fromString(accountId))
			?: throw IeumException(FamilyErrorCode.FAMILY_NOT_FOUND)

		if (member.role != FamilyRole.OWNER) {
			throw IeumException(FamilyErrorCode.FAMILY_ACCESS_DENIED)
		}
	}

	private fun findInvitedFamily(code: String): Family {
		val invitation = familyInvitationRepository.findByCodeAndIsDeletedFalse(code)
			?.takeUnless { it.isExpired() }
			?: throw IeumException(FamilyErrorCode.INVITATION_NOT_FOUND)

		return familyRepository.findByFamilyIdAndIsDeletedFalse(invitation.familyId)
			?: throw IeumException(FamilyErrorCode.INVITATION_NOT_FOUND)
	}

	private fun generateCode(): String =
		(1..CODE_LENGTH).map { CODE_CHARS[random.nextInt(CODE_CHARS.length)] }.joinToString("")

	companion object {
		private val INVITATION_TTL = Duration.ofHours(1)
		private const val CODE_LENGTH = 8

		// 헷갈리는 0/O, 1/I는 뺀다.
		private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
		private val random = SecureRandom()
	}
}
