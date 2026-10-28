package com.ieum.expense.repository

import com.ieum.expense.domain.entity.Budget
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface BudgetRepository : JpaRepository<Budget, UUID> {

	// null 인자는 IS NULL 로 비교된다.
	fun findByFamilyIdAndAccountIdAndBudgetMonthAndIsDeletedFalse(
		familyId: UUID,
		accountId: UUID?,
		budgetMonth: LocalDate?,
	): Budget?
}
