package com.ieum.family.repository

import com.ieum.family.domain.entity.Family
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FamilyRepository : JpaRepository<Family, UUID> {

	fun findAllByFamilyIdInAndIsDeletedFalse(familyIds: Collection<UUID>): List<Family>

	fun findByFamilyIdAndIsDeletedFalse(familyId: UUID): Family?
}
