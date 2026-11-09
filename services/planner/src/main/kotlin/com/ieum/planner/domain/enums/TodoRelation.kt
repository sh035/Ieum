package com.ieum.planner.domain.enums

// 보는 사람 기준으로 할 일과의 관계를 나타낸다.
enum class TodoRelation {
	MINE,
	ASSIGNED_TO_ME,
	ASSIGNED_BY_ME,
	OTHER,
}
