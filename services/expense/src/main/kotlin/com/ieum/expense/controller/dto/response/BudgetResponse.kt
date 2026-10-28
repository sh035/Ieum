package com.ieum.expense.controller.dto.response

import com.ieum.expense.domain.enums.BudgetScope
import java.time.YearMonth

data class BudgetResponse(
	val scope: BudgetScope,
	val month: YearMonth?,
	val amount: Int,
)
