package com.ieum.planner.controller

import com.ieum.planner.controller.dto.request.CreateTodoRequest
import com.ieum.planner.controller.dto.request.UpdateTodoRequest
import com.ieum.planner.service.TodoService
import com.ieum.web.response.BaseResponseEntity
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/todo")
class TodoController(
	private val todoService: TodoService,
) {

	@PostMapping
	fun createTodo(
		@AuthenticationPrincipal jwt: Jwt,
		@Valid @RequestBody request: CreateTodoRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.created(todoService.createTodo(requireNotNull(jwt.subject), request))
	}

	@GetMapping
	fun getTodos(
		@AuthenticationPrincipal jwt: Jwt,
		@RequestParam(required = false) familyId: UUID?,
	): BaseResponseEntity {
		return BaseResponseEntity.single(todoService.getTodos(requireNotNull(jwt.subject), familyId))
	}

	@GetMapping("/matrix")
	fun getMatrix(
		@AuthenticationPrincipal jwt: Jwt,
		@RequestParam(required = false) familyId: UUID?,
	): BaseResponseEntity {
		return BaseResponseEntity.single(todoService.getMatrix(requireNotNull(jwt.subject), familyId))
	}

	@GetMapping("/{todoId}")
	fun getTodo(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable todoId: UUID,
	): BaseResponseEntity {
		return BaseResponseEntity.single(todoService.getTodo(requireNotNull(jwt.subject), todoId))
	}

	@PatchMapping("/{todoId}")
	fun updateTodo(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable todoId: UUID,
		@Valid @RequestBody request: UpdateTodoRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.single(todoService.updateTodo(requireNotNull(jwt.subject), todoId, request))
	}

	@DeleteMapping("/{todoId}")
	fun deleteTodo(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable todoId: UUID,
	): BaseResponseEntity {
		todoService.deleteTodo(requireNotNull(jwt.subject), todoId)
		return BaseResponseEntity.emptyNoContent()
	}

	@PostMapping("/{todoId}/complete")
	fun completeTodo(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable todoId: UUID,
	): BaseResponseEntity {
		todoService.updateCompletion(requireNotNull(jwt.subject), todoId, isCompleted = true)
		return BaseResponseEntity.emptyNoContent()
	}

	@DeleteMapping("/{todoId}/complete")
	fun cancelTodoCompletion(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable todoId: UUID,
	): BaseResponseEntity {
		todoService.updateCompletion(requireNotNull(jwt.subject), todoId, isCompleted = false)
		return BaseResponseEntity.emptyNoContent()
	}
}
