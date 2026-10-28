package com.ieum.planner.controller.dto.request

import com.ieum.planner.domain.enums.TodoPriority
import com.ieum.planner.domain.enums.TodoVisibility
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class CreateTodoRequest(
	val familyId: UUID?,
	val visibility: TodoVisibility,
	@field:NotBlank
	@field:Size(max = 255)
	val title: String,
	@field:Size(max = 2000)
	val memo: String?,
	val priority: TodoPriority = TodoPriority.NONE,
	val dueDate: LocalDate?,
	val dueTime: LocalTime?,
	val assigneeAccountId: UUID?,
)
