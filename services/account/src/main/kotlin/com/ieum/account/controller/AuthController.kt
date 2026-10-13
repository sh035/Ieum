package com.ieum.account.controller

import com.ieum.account.controller.dto.request.ReissueRequest
import com.ieum.account.controller.dto.request.SignUpRequest
import com.ieum.account.service.AccountService
import com.ieum.web.response.BaseResponseEntity
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
	private val accountService: AccountService,
) {

	@PostMapping("/sign-up")
	fun signUp(
		@Valid @RequestBody request: SignUpRequest,
	): BaseResponseEntity {
		accountService.signUp(request)

		return BaseResponseEntity.created()
	}

	@PostMapping("/refresh")
	fun reissue(
		@Valid @RequestBody request: ReissueRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.single(accountService.reissue(request.refreshToken))
	}

	@PostMapping("/logout")
	fun logout(
		@Valid @RequestBody request: ReissueRequest,
	): BaseResponseEntity {
		accountService.logout(request.refreshToken)

		return BaseResponseEntity.emptyNoContent()
	}
}
