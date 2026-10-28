package com.ieum.expense.repository

import com.ieum.expense.domain.entity.Expense
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ExpenseRepository : JpaRepository<Expense, UUID> {

	fun findByExpenseIdAndIsDeletedFalse(expenseId: UUID): Expense?
}
