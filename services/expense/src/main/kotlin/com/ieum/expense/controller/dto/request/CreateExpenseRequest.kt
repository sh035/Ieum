package com.ieum.expense.controller.dto.request

import com.ieum.expense.domain.enums.ExpenseCategory
import com.ieum.expense.domain.enums.Visibility
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

data class CreateExpenseRequest(
	val familyId: UUID,
	val visibility: Visibility,
	@field:NotBlank
	@field:Size(max = 100)
	val title: String,
	@field:Size(max = 2000)
	val memo: String?,
	@field:Positive
	val amount: Int,
	val category: ExpenseCategory,
	val spentDate: LocalDate,
	val payerAccountId: UUID?,
)
