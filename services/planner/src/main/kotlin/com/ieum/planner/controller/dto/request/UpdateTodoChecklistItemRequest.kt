package com.ieum.planner.controller.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateTodoChecklistItemRequest(
	@field:NotBlank
	@field:Size(max = 255)
	val content: String,
)
