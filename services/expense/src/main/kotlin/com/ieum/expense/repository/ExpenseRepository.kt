package com.ieum.expense.repository

import com.ieum.expense.domain.entity.Expense
import com.ieum.expense.domain.enums.Visibility
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface ExpenseRepository : JpaRepository<Expense, UUID> {

	fun findByExpenseIdAndIsDeletedFalse(expenseId: UUID): Expense?

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
