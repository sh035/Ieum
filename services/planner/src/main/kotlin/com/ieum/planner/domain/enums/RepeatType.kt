package com.ieum.planner.domain.enums

import java.time.LocalDateTime

// 매주는 첫 회차의 요일, 매월은 첫 회차의 날짜다. 없는 날짜는 그달 말일이 된다.
enum class RepeatType {
	DAILY,
	WEEKLY,
	MONTHLY,
	;

	// 항상 첫 회차에서 계산해야 1월 31일 → 2월 28일 → 3월 31일처럼 말일 보정이 쌓이지 않는다.
	fun occurrence(first: LocalDateTime, index: Long): LocalDateTime = when (this) {
		DAILY -> first.plusDays(index)
		WEEKLY -> first.plusWeeks(index)
		MONTHLY -> first.plusMonths(index)
	}
}
