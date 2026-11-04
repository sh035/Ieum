package com.ieum.expense.service

import com.ieum.expense.repository.BudgetRepository
import com.ieum.expense.repository.ExpenseRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

// family 의 outbox 이벤트는 두 번 올 수 있어서 다시 처리해도 결과가 같게 만든다.
@Service
class FamilyEventService(
	private val expenseRepository: ExpenseRepository,
	private val budgetRepository: BudgetRepository,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// 개인 지출과 개인 예산도 그 가족 안에서만 쓰이므로 함께 지운다.
	@Transactional
	fun deleteFamily(familyId: UUID) {
		val expenses = expenseRepository.softDeleteAllByFamilyId(familyId, LocalDateTime.now())
		budgetRepository.findAllByFamilyIdAndIsDeletedFalse(familyId).forEach { it.delete() }

		log.debug("Expense family data deleted. familyId={}, expenses={}", familyId, expenses)
	}
}
