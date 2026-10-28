package com.ieum.planner.repository

import com.ieum.planner.domain.entity.Todo
import com.ieum.planner.domain.enums.TodoVisibility
import org.springframework.data.jpa.repository.JpaRepository
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
		visibility: TodoVisibility,
	): List<Todo>

	fun findAllByCreatorAccountIdAndVisibilityAndIsDeletedFalseOrderByCreatedAt(
		creatorAccountId: UUID,
		visibility: TodoVisibility,
	): List<Todo>
}
