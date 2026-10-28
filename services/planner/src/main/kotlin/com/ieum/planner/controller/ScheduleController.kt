package com.ieum.planner.controller

import com.ieum.planner.controller.dto.request.CreateScheduleRequest
import com.ieum.planner.controller.dto.request.UpdateScheduleRequest
import com.ieum.planner.service.ScheduleService
import com.ieum.web.response.BaseResponseEntity
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/schedule")
class ScheduleController(
	private val scheduleService: ScheduleService,
) {

	@PostMapping
	fun createSchedule(
		@AuthenticationPrincipal jwt: Jwt,
		@Valid @RequestBody request: CreateScheduleRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.created(scheduleService.createSchedule(requireNotNull(jwt.subject), request))
	}

	@GetMapping
	fun getSchedules(
		@AuthenticationPrincipal jwt: Jwt,
		@RequestParam(required = false) familyId: UUID?,
		@RequestParam from: LocalDate,
		@RequestParam to: LocalDate,
	): BaseResponseEntity {
		return BaseResponseEntity.single(scheduleService.getSchedules(requireNotNull(jwt.subject), familyId, from, to))
	}

	@GetMapping("/{scheduleId}")
	fun getSchedule(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable scheduleId: UUID,
	): BaseResponseEntity {
		return BaseResponseEntity.single(scheduleService.getSchedule(requireNotNull(jwt.subject), scheduleId))
	}

	@PatchMapping("/{scheduleId}")
	fun updateSchedule(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable scheduleId: UUID,
		@Valid @RequestBody request: UpdateScheduleRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.single(scheduleService.updateSchedule(requireNotNull(jwt.subject), scheduleId, request))
	}

	@DeleteMapping("/{scheduleId}")
	fun deleteSchedule(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable scheduleId: UUID,
	): BaseResponseEntity {
		scheduleService.deleteSchedule(requireNotNull(jwt.subject), scheduleId)
		return BaseResponseEntity.emptyNoContent()
	}
}
