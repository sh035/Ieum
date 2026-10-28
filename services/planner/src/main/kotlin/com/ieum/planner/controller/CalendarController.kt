package com.ieum.planner.controller

import com.ieum.planner.service.CalendarService
import com.ieum.web.response.BaseResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/calendar")
class CalendarController(
	private val calendarService: CalendarService,
) {

	@GetMapping
	fun getCalendar(
		@AuthenticationPrincipal jwt: Jwt,
		@RequestParam(required = false) familyId: UUID?,
		@RequestParam from: LocalDate,
		@RequestParam to: LocalDate,
	): BaseResponseEntity {
		return BaseResponseEntity.single(calendarService.getCalendar(requireNotNull(jwt.subject), familyId, from, to))
	}
}
