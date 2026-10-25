package com.ieum.family.controller

import com.ieum.family.controller.dto.request.CreateFamilyRequest
import com.ieum.family.service.FamilyService
import com.ieum.web.response.BaseResponseEntity
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/family")
class FamilyController(
	private val familyService: FamilyService,
) {

	@PostMapping
	fun createFamily(
		@AuthenticationPrincipal jwt: Jwt,
		@Valid @RequestBody request: CreateFamilyRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.created(familyService.createFamily(requireNotNull(jwt.subject), request.name))
	}

	@GetMapping
	fun getMyFamilies(
		@AuthenticationPrincipal jwt: Jwt,
	): BaseResponseEntity {
		return BaseResponseEntity.single(familyService.getMyFamilies(requireNotNull(jwt.subject)))
	}
}
