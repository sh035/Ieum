package com.ieum.planner.controller.dto.response

data class BriefingCountResponse(
	val todo: Int = 0,
	val overdueTodo: Int = 0,
	val schedule: Int = 0,
)
