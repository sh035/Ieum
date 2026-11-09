package com.ieum.grocery.controller

import com.ieum.grocery.service.GroceryItemService
import com.ieum.web.response.BaseResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/grocery/daily-briefing")
class GroceryDailyBriefingController(
	private val groceryItemService: GroceryItemService,
) {

	@GetMapping
	fun getDailyBriefing(@AuthenticationPrincipal jwt: Jwt): BaseResponseEntity {
		return BaseResponseEntity.single(groceryItemService.getDailyBriefing(requireNotNull(jwt.subject)))
	}
}
