package com.ieum.planner.controller.dto.request

import com.ieum.planner.domain.enums.TodoPriority
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// 수정 가능한 필드를 모두 받는다. null은 값을 비운다는 뜻이다.
data class UpdateTodoRequest(
	@field:NotBlank
	@field:Size(max = 255)
	val title: String,
	@field:Size(max = 2000)
	val memo: String?,
	val priority: TodoPriority,
	val dueDate: LocalDate?,
	val dueTime: LocalTime?,
	val assigneeAccountId: UUID?,
)
