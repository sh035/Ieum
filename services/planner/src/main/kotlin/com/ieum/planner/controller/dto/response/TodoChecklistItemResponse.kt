package com.ieum.planner.controller.dto.response

import java.util.UUID

data class TodoChecklistItemResponse(
	val todoChecklistItemId: UUID,
	val content: String,
	val isCompleted: Boolean,
)
