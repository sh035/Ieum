package com.ieum.planner.controller.dto.request

import com.ieum.planner.domain.enums.Visibility
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.UUID

data class CreateScheduleRequest(
	val familyId: UUID?,
	val visibility: Visibility,
	@field:NotBlank
	@field:Size(max = 255)
	val title: String,
	@field:Size(max = 2000)
	val memo: String?,
	val isAllDay: Boolean,
	val startAt: LocalDateTime,
	val endAt: LocalDateTime,
	val attendeeAccountIds: List<UUID> = emptyList(),
	// 지금 구성원 전체를 참석자로 넣는다. 나중에 들어온 가족은 수정에서 따로 넣는다.
	val isAllFamily: Boolean = false,
)
