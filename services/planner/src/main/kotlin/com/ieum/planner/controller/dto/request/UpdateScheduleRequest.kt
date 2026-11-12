package com.ieum.planner.controller.dto.request

import com.ieum.planner.domain.enums.RepeatType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// 수정 가능한 필드를 모두 받는다. 참석자는 보낸 목록으로 교체한다. 반복은 이후 모두를 고칠 때만 바꿀 수 있다.
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
	// 없으면 반복하지 않는다. 매주는 시작일의 요일, 매월은 시작일의 날짜다.
	val repeatType: RepeatType? = null,
	// 마지막 회차 날짜(포함). 없으면 끝없이 반복한다.
	val repeatUntil: LocalDate? = null,
)
