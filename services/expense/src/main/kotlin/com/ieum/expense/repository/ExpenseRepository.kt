package com.ieum.expense.repository

import com.ieum.expense.domain.entity.Expense
import com.ieum.expense.domain.enums.Visibility
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

interface ExpenseRepository : JpaRepository<Expense, UUID> {

	fun findByExpenseIdAndIsDeletedFalse(expenseId: UUID): Expense?

	// 오래된 가족은 지출이 많아서 한 문장으로 지운다. @PreUpdate 를 거치지 않아 수정 시각과 수정자(시스템)를 직접 넣는다.
	@Modifying
	@Query("update Expense e set e.isDeleted = true, e.updatedAt = :now, e.updatedBy = null where e.familyId = :familyId and e.isDeleted = false")
	fun softDeleteAllByFamilyId(familyId: UUID, now: LocalDateTime): Int

	fun findAllByFamilyIdAndVisibilityAndSpentDateBetweenAndIsDeletedFalse(
		familyId: UUID,
		visibility: Visibility,
		from: LocalDate,
		to: LocalDate,
	): List<Expense>

	fun findAllByFamilyIdAndVisibilityAndCreatorAccountIdAndSpentDateBetweenAndIsDeletedFalse(
		familyId: UUID,
		visibility: Visibility,
		creatorAccountId: UUID,
		from: LocalDate,
		to: LocalDate,
	): List<Expense>
}
