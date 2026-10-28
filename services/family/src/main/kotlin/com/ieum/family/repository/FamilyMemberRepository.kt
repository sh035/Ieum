package com.ieum.family.repository

import com.ieum.family.domain.entity.FamilyMember
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FamilyMemberRepository : JpaRepository<FamilyMember, UUID> {

	fun findAllByAccountIdAndIsDeletedFalseOrderByCreatedAt(accountId: UUID): List<FamilyMember>

	fun findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId: UUID): List<FamilyMember>

	fun findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId: UUID, accountId: UUID): FamilyMember?

	fun findByFamilyMemberIdAndFamilyIdAndIsDeletedFalse(familyMemberId: UUID, familyId: UUID): FamilyMember?

	fun existsByFamilyIdAndAccountIdAndIsDeletedFalse(familyId: UUID, accountId: UUID): Boolean
}
