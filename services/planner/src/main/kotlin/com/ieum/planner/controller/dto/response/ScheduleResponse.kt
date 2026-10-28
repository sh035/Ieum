package com.ieum.planner.controller.dto.response

import com.ieum.planner.domain.enums.Visibility
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
)
