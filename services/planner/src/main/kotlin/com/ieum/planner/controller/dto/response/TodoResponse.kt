package com.ieum.planner.controller.dto.response

import com.ieum.planner.domain.enums.TodoPriority
import com.ieum.planner.domain.enums.Visibility
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class TodoResponse(
	val todoId: UUID,
	val familyId: UUID?,
	val creatorAccountId: UUID,
	val assigneeAccountId: UUID,
	val title: String,
	val memo: String?,
	val visibility: Visibility,
	val priority: TodoPriority,
	val dueDate: LocalDate?,
	val dueTime: LocalTime?,
	val isCompleted: Boolean,
	val checklistItems: List<TodoChecklistItemResponse>,
)
