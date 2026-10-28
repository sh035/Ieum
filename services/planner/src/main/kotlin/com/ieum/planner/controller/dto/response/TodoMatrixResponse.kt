package com.ieum.planner.controller.dto.response

data class TodoMatrixResponse(
	val importantUrgent: List<TodoResponse>,
	val importantNotUrgent: List<TodoResponse>,
	val notImportantUrgent: List<TodoResponse>,
	val notImportantNotUrgent: List<TodoResponse>,
)
