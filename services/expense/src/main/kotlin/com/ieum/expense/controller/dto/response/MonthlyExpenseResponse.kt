package com.ieum.expense.controller.dto.response

import com.ieum.expense.domain.enums.ExpenseCategory
import java.time.YearMonth

data class MonthlyExpenseResponse(
	val month: YearMonth,
	val family: ExpenseSection,
	val personal: ExpenseSection,
) {

	// budget 은 볼 권한이 없거나 아직 정하지 않았으면 null 이다.
	data class ExpenseSection(
		val total: Long,
		val categoryTotals: Map<ExpenseCategory, Long>,
		val budget: BudgetSummary?,
		val expenses: List<ExpenseResponse>,
	)

	// 예산을 넘겨 쓰면 remaining 은 음수다.
	data class BudgetSummary(
		val amount: Int,
		val remaining: Long,
	)
}
