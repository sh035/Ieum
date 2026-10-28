package com.ieum.expense.controller.dto.response

import com.ieum.expense.domain.enums.ExpenseCategory
import com.ieum.expense.domain.enums.Visibility
import java.time.LocalDate
import java.util.UUID

data class ExpenseResponse(
	val expenseId: UUID,
	val familyId: UUID,
	val creatorAccountId: UUID,
	val payerAccountId: UUID,
	val title: String,
	val memo: String?,
	val amount: Int,
	val category: ExpenseCategory,
	val visibility: Visibility,
	val spentDate: LocalDate,
)
