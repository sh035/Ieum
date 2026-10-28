package com.ieum.expense.controller

import com.ieum.expense.controller.dto.request.CreateExpenseRequest
import com.ieum.expense.controller.dto.request.UpdateExpenseRequest
import com.ieum.expense.service.ExpenseService
import com.ieum.web.response.BaseResponseEntity
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/expense")
class ExpenseController(
	private val expenseService: ExpenseService,
) {

	@PostMapping
	fun createExpense(
		@AuthenticationPrincipal jwt: Jwt,
		@Valid @RequestBody request: CreateExpenseRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.created(expenseService.createExpense(requireNotNull(jwt.subject), request))
	}

	@GetMapping("/{expenseId}")
	fun getExpense(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable expenseId: UUID,
	): BaseResponseEntity {
		return BaseResponseEntity.single(expenseService.getExpense(requireNotNull(jwt.subject), expenseId))
	}

	@PatchMapping("/{expenseId}")
	fun updateExpense(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable expenseId: UUID,
		@Valid @RequestBody request: UpdateExpenseRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.single(expenseService.updateExpense(requireNotNull(jwt.subject), expenseId, request))
	}

	@DeleteMapping("/{expenseId}")
	fun deleteExpense(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable expenseId: UUID,
	): BaseResponseEntity {
		expenseService.deleteExpense(requireNotNull(jwt.subject), expenseId)
		return BaseResponseEntity.emptyNoContent()
	}
}
