package com.ieum.expense.controller

import com.ieum.expense.controller.dto.request.UpdateBudgetRequest
import com.ieum.expense.domain.enums.BudgetScope
import com.ieum.expense.service.BudgetService
import com.ieum.web.response.BaseResponseEntity
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth
import java.util.UUID

@RestController
@RequestMapping("/api/v1/expense/budget")
class BudgetController(
	private val budgetService: BudgetService,
) {

	@PatchMapping
	fun updateBudget(
		@AuthenticationPrincipal jwt: Jwt,
		@Valid @RequestBody request: UpdateBudgetRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.single(budgetService.updateBudget(requireNotNull(jwt.subject), request))
	}

	@DeleteMapping
	fun deleteBudget(
		@AuthenticationPrincipal jwt: Jwt,
		@RequestParam familyId: UUID,
		@RequestParam scope: BudgetScope,
		@RequestParam(required = false) month: YearMonth?,
	): BaseResponseEntity {
		budgetService.deleteBudget(requireNotNull(jwt.subject), familyId, scope, month)
		return BaseResponseEntity.emptyNoContent()
	}
}
