package com.ieum.planner.controller.dto.response

import com.ieum.planner.domain.enums.RepeatType
import com.ieum.planner.domain.enums.Visibility
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class ScheduleResponse(
	val scheduleId: UUID,
	val familyId: UUID?,
	val creatorAccountId: UUID,
	val title: String,
	val memo: String?,
	val visibility: Visibility,
	val isAllDay: Boolean,
	val startAt: LocalDateTime,
	val endAt: LocalDateTime,
	val attendeeAccountIds: List<UUID>,
	val repeatType: RepeatType?,
	val repeatUntil: LocalDate?,
	// 반복의 한 회차를 따로 바꾼 일정이면 원본 ID 다.
	val recurringScheduleId: UUID?,
	// 반복 일정의 몇 번째 회차인지(원래 시작)다. 수정 / 삭제 / 상세 조회에 그대로 보낸다. 반복이 아니면 null 이다.
	val occurrenceStartAt: LocalDateTime?,
)
