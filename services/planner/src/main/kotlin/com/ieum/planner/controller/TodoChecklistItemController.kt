package com.ieum.planner.controller

import com.ieum.planner.controller.dto.request.CreateTodoChecklistItemRequest
import com.ieum.planner.controller.dto.request.UpdateTodoChecklistItemRequest
import com.ieum.planner.service.TodoChecklistItemService
import com.ieum.web.response.BaseResponseEntity
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/todo/{todoId}/checklist-item")
class TodoChecklistItemController(
	private val todoChecklistItemService: TodoChecklistItemService,
) {

	@PostMapping
	fun addItem(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable todoId: UUID,
		@Valid @RequestBody request: CreateTodoChecklistItemRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.created(todoChecklistItemService.addItem(requireNotNull(jwt.subject), todoId, request))
	}

	@PatchMapping("/{itemId}")
	fun updateItem(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable todoId: UUID,
		@PathVariable itemId: UUID,
		@Valid @RequestBody request: UpdateTodoChecklistItemRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.single(
			todoChecklistItemService.updateItem(requireNotNull(jwt.subject), todoId, itemId, request),
		)
	}

	@DeleteMapping("/{itemId}")
	fun deleteItem(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable todoId: UUID,
		@PathVariable itemId: UUID,
	): BaseResponseEntity {
		todoChecklistItemService.deleteItem(requireNotNull(jwt.subject), todoId, itemId)
		return BaseResponseEntity.emptyNoContent()
	}

	@PostMapping("/{itemId}/complete")
	fun completeItem(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable todoId: UUID,
		@PathVariable itemId: UUID,
	): BaseResponseEntity {
		todoChecklistItemService.updateCompletion(requireNotNull(jwt.subject), todoId, itemId, isCompleted = true)
		return BaseResponseEntity.emptyNoContent()
	}

	@DeleteMapping("/{itemId}/complete")
	fun cancelItemCompletion(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable todoId: UUID,
		@PathVariable itemId: UUID,
	): BaseResponseEntity {
		todoChecklistItemService.updateCompletion(requireNotNull(jwt.subject), todoId, itemId, isCompleted = false)
		return BaseResponseEntity.emptyNoContent()
	}
}
