package com.ieum.family.repository

import com.ieum.family.domain.entity.FamilyMember
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FamilyMemberRepository : JpaRepository<FamilyMember, UUID> {

	fun findAllByAccountIdAndIsDeletedFalseOrderByCreatedAt(accountId: UUID): List<FamilyMember>
}
