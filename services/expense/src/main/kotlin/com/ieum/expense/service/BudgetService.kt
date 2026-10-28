package com.ieum.expense.service

import com.ieum.expense.controller.dto.request.UpdateBudgetRequest
import com.ieum.expense.controller.dto.response.BudgetResponse
import com.ieum.expense.domain.entity.Budget
import com.ieum.expense.domain.enums.BudgetScope
import com.ieum.expense.domain.enums.FamilyRole
import com.ieum.expense.exception.ExpenseErrorCode
import com.ieum.expense.repository.BudgetRepository
import com.ieum.web.exception.IeumException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.YearMonth
import java.util.UUID

@Service
class BudgetService(
	private val budgetRepository: BudgetRepository,
	private val familyClient: FamilyClient,
) {

	fun updateBudget(accountId: String, request: UpdateBudgetRequest): BudgetResponse {
		val owner = budgetOwner(UUID.fromString(accountId), request.familyId, request.scope)
		val budgetMonth = request.month?.atDay(1)

		val budget = budgetRepository.findByFamilyIdAndAccountIdAndBudgetMonthAndIsDeletedFalse(
			request.familyId,
			owner,
			budgetMonth,
		)?.apply { amount = request.amount }
			?: Budget(familyId = request.familyId, accountId = owner, budgetMonth = budgetMonth, amount = request.amount)

		// 같은 예산을 동시에 처음 만들면 unique index 에 걸린다.
		try {
			budgetRepository.saveAndFlush(budget)
		} catch (e: DataIntegrityViolationException) {
			throw IeumException(ExpenseErrorCode.BUDGET_CONFLICT)
		}

		return BudgetResponse(scope = request.scope, month = request.month, amount = budget.amount)
	}

	// 그 달 예산을 지우면 기본 예산이 다시 적용된다.
	fun deleteBudget(accountId: String, familyId: UUID, scope: BudgetScope, month: YearMonth?) {
		val owner = budgetOwner(UUID.fromString(accountId), familyId, scope)
		val budget = budgetRepository.findByFamilyIdAndAccountIdAndBudgetMonthAndIsDeletedFalse(familyId, owner, month?.atDay(1))
			?: throw IeumException(ExpenseErrorCode.BUDGET_NOT_FOUND)

		budget.delete()
		budgetRepository.save(budget)
	}

	// 그 달 예산이 있으면 그것을, 없으면 기본 예산을 쓴다. accountId 가 null 이면 가족 예산이다.
	fun findEffectiveAmount(familyId: UUID, accountId: UUID?, month: YearMonth): Int? =
		(
			budgetRepository.findByFamilyIdAndAccountIdAndBudgetMonthAndIsDeletedFalse(familyId, accountId, month.atDay(1))
				?: budgetRepository.findByFamilyIdAndAccountIdAndBudgetMonthAndIsDeletedFalse(familyId, accountId, null)
			)?.amount

	// 가족 예산은 OWNER / MANAGER 만 다루고, 개인 예산은 본인 것만 다룬다. 가족 예산의 주인은 null 로 표현한다.
	private fun budgetOwner(me: UUID, familyId: UUID, scope: BudgetScope): UUID? {
		val role = familyClient.getRole(familyId, me) ?: throw IeumException(ExpenseErrorCode.FAMILY_NOT_FOUND)

		return when (scope) {
			BudgetScope.FAMILY -> {
				if (!canManageFamilyBudget(role)) {
					throw IeumException(ExpenseErrorCode.BUDGET_ACCESS_DENIED)
				}
				null
			}
			BudgetScope.PERSONAL -> me
		}
	}

	companion object {
		fun canManageFamilyBudget(role: FamilyRole) = role == FamilyRole.OWNER || role == FamilyRole.MANAGER
	}
}
