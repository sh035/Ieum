package com.ieum.expense.controller.dto.request

import com.ieum.expense.domain.enums.ExpenseCategory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

// 수정 가능한 필드를 모두 보낸다. memo 의 null 은 값을 지운다.
data class UpdateExpenseRequest(
	@field:NotBlank
	@field:Size(max = 100)
	val title: String,
	@field:Size(max = 2000)
	val memo: String?,
	@field:Positive
	val amount: Int,
	val category: ExpenseCategory,
	val spentDate: LocalDate,
	val payerAccountId: UUID,
)
