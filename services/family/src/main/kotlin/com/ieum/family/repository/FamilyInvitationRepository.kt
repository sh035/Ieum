package com.ieum.family.repository

import com.ieum.family.domain.entity.FamilyInvitation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FamilyInvitationRepository : JpaRepository<FamilyInvitation, UUID> {

	fun findByFamilyIdAndIsDeletedFalse(familyId: UUID): FamilyInvitation?

	fun findByCodeAndIsDeletedFalse(code: String): FamilyInvitation?
}
