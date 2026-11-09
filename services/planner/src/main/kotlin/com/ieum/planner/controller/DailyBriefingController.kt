package com.ieum.planner.controller

import com.ieum.planner.service.DailyBriefingService
import com.ieum.web.response.BaseResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/daily-briefing")
class DailyBriefingController(
	private val dailyBriefingService: DailyBriefingService,
) {

	@GetMapping
	fun getDailyBriefing(@AuthenticationPrincipal jwt: Jwt): BaseResponseEntity {
		return BaseResponseEntity.single(dailyBriefingService.getDailyBriefing(requireNotNull(jwt.subject)))
	}
}
