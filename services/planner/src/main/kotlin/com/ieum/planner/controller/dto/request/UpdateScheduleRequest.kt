package com.ieum.planner.controller.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.UUID

// 수정 가능한 필드를 모두 받는다. 참석자는 보낸 목록으로 교체한다.
data class UpdateScheduleRequest(
	@field:NotBlank
	@field:Size(max = 255)
	val title: String,
	@field:Size(max = 2000)
	val memo: String?,
	val isAllDay: Boolean,
	val startAt: LocalDateTime,
	val endAt: LocalDateTime,
	val attendeeAccountIds: List<UUID> = emptyList(),
)
