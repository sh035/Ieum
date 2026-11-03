package com.ieum.grpc.client.planner

data class PlannerBriefingCounts(
	val todo: Int,
	val overdueTodo: Int,
	val schedule: Int,
)
