package com.ieum.planner.controller.dto.response

data class CalendarResponse(
	val todos: List<TodoResponse>,
	val schedules: List<ScheduleResponse>,
)
