package com.ieum.expense.service

import com.ieum.expense.controller.dto.request.CreateExpenseRequest
import com.ieum.expense.controller.dto.request.UpdateExpenseRequest
import com.ieum.expense.controller.dto.response.ExpenseResponse
import com.ieum.expense.controller.dto.response.MonthlyExpenseResponse
import com.ieum.expense.controller.dto.response.MonthlyExpenseResponse.BudgetSummary
import com.ieum.expense.controller.dto.response.MonthlyExpenseResponse.ExpenseSection
import com.ieum.expense.domain.entity.Expense
import com.ieum.expense.domain.enums.ExpenseCategory
import com.ieum.expense.domain.enums.Visibility
import com.ieum.expense.exception.ExpenseErrorCode
import com.ieum.expense.repository.ExpenseRepository
import com.ieum.web.exception.IeumException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth
import java.util.UUID

@Service
class ExpenseService(
	private val expenseRepository: ExpenseRepository,
	private val budgetService: BudgetService,
	private val familyClient: FamilyClient,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun createExpense(accountId: String, request: CreateExpenseRequest): ExpenseResponse {
		val me = UUID.fromString(accountId)
		if (!familyClient.isMember(request.familyId, me)) {
			throw IeumException(ExpenseErrorCode.FAMILY_NOT_FOUND)
		}

		val expense = expenseRepository.save(
			Expense(
				familyId = request.familyId,
				creatorAccountId = me,
				payerAccountId = resolvePayer(request.familyId, request.visibility, me, request.payerAccountId ?: me),
				title = request.title.trim(),
				memo = request.memo,
				amount = request.amount,
				category = request.category,
				visibility = request.visibility,
				spentDate = request.spentDate,
			),
		)

		log.debug("Expense created. expenseId={}, accountId={}", expense.expenseId, accountId)

		return expense.toResponse()
	}

	// 가족 지출과 내 개인 지출을 따로 집계한다. 가족 예산은 OWNER / MANAGER 에게만 내려준다.
	@Transactional(readOnly = true)
	fun getMonthly(accountId: String, familyId: UUID, month: YearMonth): MonthlyExpenseResponse {
		val me = UUID.fromString(accountId)
		val role = familyClient.getRole(familyId, me) ?: throw IeumException(ExpenseErrorCode.FAMILY_NOT_FOUND)
		val from = month.atDay(1)
		val to = month.atEndOfMonth()

		val familyExpenses = expenseRepository
			.findAllByFamilyIdAndVisibilityAndSpentDateBetweenAndIsDeletedFalse(familyId, Visibility.FAMILY, from, to)
		val personalExpenses = expenseRepository
			.findAllByFamilyIdAndVisibilityAndCreatorAccountIdAndSpentDateBetweenAndIsDeletedFalse(
				familyId,
				Visibility.PRIVATE,
				me,
				from,
				to,
			)
		val familyBudget = if (BudgetService.canManageFamilyBudget(role)) {
			budgetService.findEffectiveAmount(familyId, null, month)
		} else {
			null
		}

		return MonthlyExpenseResponse(
			month = month,
			family = section(familyExpenses, familyBudget),
			personal = section(personalExpenses, budgetService.findEffectiveAmount(familyId, me, month)),
		)
	}

	fun getExpense(accountId: String, expenseId: UUID): ExpenseResponse =
		findAccessibleExpense(UUID.fromString(accountId), expenseId).toResponse()

	fun updateExpense(accountId: String, expenseId: UUID, request: UpdateExpenseRequest): ExpenseResponse {
		val me = UUID.fromString(accountId)
		val expense = findAccessibleExpense(me, expenseId)

		// 결제한 사람을 바꿀 때만 확인한다. 기존 결제자가 가족을 나가도 다른 필드는 고칠 수 있어야 한다.
		if (request.payerAccountId != expense.payerAccountId) {
			expense.payerAccountId = resolvePayer(expense.familyId, expense.visibility, me, request.payerAccountId)
		}
		expense.title = request.title.trim()
		expense.memo = request.memo
		expense.amount = request.amount
		expense.category = request.category
		expense.spentDate = request.spentDate

		return expenseRepository.save(expense).toResponse()
	}

	fun deleteExpense(accountId: String, expenseId: UUID) {
		val expense = findAccessibleExpense(UUID.fromString(accountId), expenseId)
		expense.delete()
		expenseRepository.save(expense)
	}

	// 볼 수 없는 지출은 존재 자체를 숨긴다.
	private fun findAccessibleExpense(me: UUID, expenseId: UUID): Expense {
		val expense = expenseRepository.findByExpenseIdAndIsDeletedFalse(expenseId)
			?: throw IeumException(ExpenseErrorCode.EXPENSE_NOT_FOUND)

		val accessible = when (expense.visibility) {
			Visibility.FAMILY -> familyClient.isMember(expense.familyId, me)
			Visibility.PRIVATE -> expense.creatorAccountId == me
		}
		if (!accessible) {
			throw IeumException(ExpenseErrorCode.EXPENSE_NOT_FOUND)
		}

		return expense
	}

	// 개인 지출은 본인이 결제한 것만, 가족 지출은 본인 또는 가족 구성원이 결제한 것으로 기록한다.
	private fun resolvePayer(familyId: UUID, visibility: Visibility, me: UUID, payerAccountId: UUID): UUID {
		val valid = when (visibility) {
			Visibility.PRIVATE -> payerAccountId == me
			Visibility.FAMILY -> payerAccountId == me || familyClient.isMember(familyId, payerAccountId)
		}
		if (!valid) {
			throw IeumException(ExpenseErrorCode.INVALID_PAYER)
		}
		return payerAccountId
	}

	// 차트에서 빠진 키를 따로 다루지 않도록 모든 카테고리를 0 원부터 채운다.
	private fun section(expenses: List<Expense>, budgetAmount: Int?): ExpenseSection {
		val total = expenses.sumOf { it.amount.toLong() }

		return ExpenseSection(
			total = total,
			categoryTotals = ExpenseCategory.entries.associateWith { category ->
				expenses.filter { it.category == category }.sumOf { it.amount.toLong() }
			},
			budget = budgetAmount?.let { BudgetSummary(amount = it, remaining = it - total) },
			expenses = expenses
				.sortedWith(compareByDescending<Expense> { it.spentDate }.thenByDescending { it.createdAt })
				.map { it.toResponse() },
		)
	}

	private fun Expense.toResponse() = ExpenseResponse(
		expenseId = requireNotNull(expenseId),
		familyId = familyId,
		creatorAccountId = creatorAccountId,
		payerAccountId = payerAccountId,
		title = title,
		memo = memo,
		amount = amount,
		category = category,
		visibility = visibility,
		spentDate = spentDate,
	)
}
