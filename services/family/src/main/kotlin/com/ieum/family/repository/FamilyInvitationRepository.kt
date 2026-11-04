package com.ieum.family.repository

import com.ieum.family.domain.entity.FamilyInvitation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FamilyInvitationRepository : JpaRepository<FamilyInvitation, UUID> {

	fun findByFamilyInvitationIdAndIsDeletedFalse(familyInvitationId: UUID): FamilyInvitation?

	fun findByFamilyInvitationIdAndFamilyIdAndIsDeletedFalse(familyInvitationId: UUID, familyId: UUID): FamilyInvitation?

	fun findByFamilyIdAndInviteeAccountIdAndIsDeletedFalse(familyId: UUID, inviteeAccountId: UUID): FamilyInvitation?

	fun findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId: UUID): List<FamilyInvitation>

	fun findAllByInviteeAccountIdAndIsDeletedFalseOrderByCreatedAt(inviteeAccountId: UUID): List<FamilyInvitation>
}
