package com.ieum.family.repository

import com.ieum.family.domain.entity.FamilyMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface FamilyMemberRepository : JpaRepository<FamilyMember, UUID> {

	fun findAllByAccountIdAndIsDeletedFalseOrderByCreatedAt(accountId: UUID): List<FamilyMember>

	fun findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId: UUID): List<FamilyMember>

	fun findAllByFamilyIdAndAccountIdInAndIsDeletedFalse(familyId: UUID, accountIds: Collection<UUID>): List<FamilyMember>

	fun findAllByFamilyIdAndAccountIdIn(familyId: UUID, accountIds: Collection<UUID>): List<FamilyMember>

	fun findByFamilyIdAndAccountIdAndIsDeletedFalse(familyId: UUID, accountId: UUID): FamilyMember?

	fun findByFamilyMemberIdAndFamilyIdAndIsDeletedFalse(familyMemberId: UUID, familyId: UUID): FamilyMember?

	// 엔티티를 영속성 컨텍스트에 올리지 않고 ID 만 읽는다. 잠그기 전에 읽은 역할이 잠근 뒤에도 남지 않게 하려는 것이다.
	@Query("select m.familyId from FamilyMember m where m.accountId = :accountId and m.isDeleted = false")
	fun findFamilyIdsByAccountId(accountId: UUID): List<UUID>

	fun countByFamilyIdAndIsDeletedFalse(familyId: UUID): Long

	fun existsByFamilyIdAndAccountIdAndIsDeletedFalse(familyId: UUID, accountId: UUID): Boolean
}
