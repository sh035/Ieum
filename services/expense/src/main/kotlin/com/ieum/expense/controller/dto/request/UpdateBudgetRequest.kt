package com.ieum.expense.controller.dto.request

import com.ieum.expense.domain.enums.BudgetScope
import jakarta.validation.constraints.PositiveOrZero
import java.time.YearMonth
import java.util.UUID

// month 가 없으면 매달 적용되는 기본 예산이다. 이미 있으면 금액을 바꾸고 없으면 만든다.
data class UpdateBudgetRequest(
	val familyId: UUID,
	val scope: BudgetScope,
	val month: YearMonth?,
	@field:PositiveOrZero
	val amount: Int,
)
