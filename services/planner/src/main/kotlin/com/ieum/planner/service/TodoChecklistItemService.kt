package com.ieum.planner.service

import com.ieum.planner.controller.dto.request.CreateTodoChecklistItemRequest
import com.ieum.planner.controller.dto.request.UpdateTodoChecklistItemRequest
import com.ieum.planner.controller.dto.response.TodoChecklistItemResponse
import com.ieum.planner.domain.entity.TodoChecklistItem
import com.ieum.planner.exception.PlannerErrorCode
import com.ieum.planner.repository.TodoChecklistItemRepository
import com.ieum.web.exception.IeumException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TodoChecklistItemService(
	private val todoService: TodoService,
	private val todoChecklistItemRepository: TodoChecklistItemRepository,
) {

	fun addItem(accountId: String, todoId: UUID, request: CreateTodoChecklistItemRequest): TodoChecklistItemResponse {
		todoService.findAccessibleTodo(UUID.fromString(accountId), todoId)

		return todoChecklistItemRepository.save(TodoChecklistItem(todoId = todoId, content = request.content)).toResponse()
	}

	fun updateItem(
		accountId: String,
		todoId: UUID,
		itemId: UUID,
		request: UpdateTodoChecklistItemRequest,
	): TodoChecklistItemResponse {
		val item = findItem(accountId, todoId, itemId)
		item.content = request.content

		return todoChecklistItemRepository.save(item).toResponse()
	}

	fun deleteItem(accountId: String, todoId: UUID, itemId: UUID) {
		val item = findItem(accountId, todoId, itemId)
		item.delete()
		todoChecklistItemRepository.save(item)
	}

	fun updateCompletion(accountId: String, todoId: UUID, itemId: UUID, isCompleted: Boolean) {
		val item = findItem(accountId, todoId, itemId)
		item.isCompleted = isCompleted
		todoChecklistItemRepository.save(item)
	}

	private fun findItem(accountId: String, todoId: UUID, itemId: UUID): TodoChecklistItem {
		todoService.findAccessibleTodo(UUID.fromString(accountId), todoId)

		return todoChecklistItemRepository.findByTodoChecklistItemIdAndTodoIdAndIsDeletedFalse(itemId, todoId)
			?: throw IeumException(PlannerErrorCode.CHECKLIST_ITEM_NOT_FOUND)
	}

	private fun TodoChecklistItem.toResponse() = TodoChecklistItemResponse(
		todoChecklistItemId = requireNotNull(todoChecklistItemId),
		content = content,
		isCompleted = isCompleted,
	)
}
