package com.ieum.expense.exception

import com.ieum.web.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ExpenseErrorCode(
	override val status: HttpStatus,
	override val message: String,
) : ErrorCode {

	FAMILY_NOT_FOUND(HttpStatus.NOT_FOUND, "Family not found."),
	EXPENSE_NOT_FOUND(HttpStatus.NOT_FOUND, "Expense not found."),
	INVALID_PAYER(HttpStatus.BAD_REQUEST, "Payer must be a family member, or yourself for a private expense."),
	FAMILY_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Family service unavailable."),
}
