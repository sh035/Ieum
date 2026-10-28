package com.ieum.planner.repository

import com.ieum.planner.domain.entity.Todo
import com.ieum.planner.domain.enums.Visibility
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface TodoRepository : JpaRepository<Todo, UUID> {

	fun findByTodoIdAndIsDeletedFalse(todoId: UUID): Todo?

	fun findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId: UUID): List<Todo>

	fun findAllByFamilyIdAndAssigneeAccountIdAndIsCompletedFalseAndIsDeletedFalse(
		familyId: UUID,
		assigneeAccountId: UUID,
	): List<Todo>

	fun findAllByAssigneeAccountIdAndVisibilityAndIsCompletedFalseAndIsDeletedFalse(
		assigneeAccountId: UUID,
		visibility: Visibility,
	): List<Todo>

	fun findAllByFamilyIdAndDueDateBetweenAndIsDeletedFalse(familyId: UUID, from: LocalDate, to: LocalDate): List<Todo>

	fun findAllByCreatorAccountIdAndVisibilityAndDueDateBetweenAndIsDeletedFalse(
		creatorAccountId: UUID,
		visibility: Visibility,
		from: LocalDate,
		to: LocalDate,
	): List<Todo>

	fun findAllByCreatorAccountIdAndVisibilityAndIsDeletedFalseOrderByCreatedAt(
		creatorAccountId: UUID,
		visibility: Visibility,
	): List<Todo>
}
