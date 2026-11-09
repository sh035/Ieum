package com.ieum.planner.repository

import com.ieum.planner.domain.entity.Todo
import com.ieum.planner.domain.enums.Visibility
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

interface TodoRepository : JpaRepository<Todo, UUID> {

	fun findByTodoIdAndIsDeletedFalse(todoId: UUID): Todo?

	fun findAllByParentTodoIdInAndIsDeletedFalse(parentTodoIds: List<UUID>): List<Todo>

	fun findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId: UUID): List<Todo>

	@Query(
		"""
		select t from Todo t
		where t.familyId = :familyId
			and (t.assigneeAccountId = :accountId or t.creatorAccountId = :accountId)
			and t.isCompleted = false and t.isDeleted = false
		""",
	)
	fun findMyOpenTodosInFamilyAndIsDeletedFalse(familyId: UUID, accountId: UUID): List<Todo>

	@Query(
		"""
		select t from Todo t
		where (t.assigneeAccountId = :accountId or t.creatorAccountId = :accountId)
			and t.dueDate <= :date
			and t.isCompleted = false and t.isDeleted = false
		""",
	)
	fun findMyOpenTodosDueUntilAndIsDeletedFalse(accountId: UUID, date: LocalDate): List<Todo>

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

	// 3시간 전 알림 후보다. 시각까지 있는 미완료 할 일만 본다.
	fun findAllByIsCompletedFalseAndIsDeletedFalseAndDueTimeIsNotNullAndDueDateBetween(from: LocalDate, to: LocalDate): List<Todo>

	// 브리핑 후보다. 오늘 마감과 기한이 지난 미완료 할 일을 함께 본다.
	fun findAllByIsCompletedFalseAndIsDeletedFalseAndDueDateLessThanEqual(date: LocalDate): List<Todo>
}
