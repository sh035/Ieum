package com.ieum.planner.repository

import com.ieum.planner.domain.entity.TodoChecklistItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TodoChecklistItemRepository : JpaRepository<TodoChecklistItem, UUID> {

	fun findAllByTodoIdInAndIsDeletedFalseOrderByCreatedAt(todoIds: Collection<UUID>): List<TodoChecklistItem>

	fun findByTodoChecklistItemIdAndTodoIdAndIsDeletedFalse(todoChecklistItemId: UUID, todoId: UUID): TodoChecklistItem?
}
