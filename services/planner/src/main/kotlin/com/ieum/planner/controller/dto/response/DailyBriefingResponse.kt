package com.ieum.planner.controller.dto.response

data class DailyBriefingResponse(
	val todos: List<TodoResponse>,
	val overdueTodos: List<TodoResponse>,
	val schedules: List<ScheduleResponse>,
)
