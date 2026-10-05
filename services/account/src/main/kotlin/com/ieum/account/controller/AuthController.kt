package com.ieum.account.controller

import com.ieum.account.controller.dto.request.LoginRequest
import com.ieum.account.controller.dto.request.ReissueRequest
import com.ieum.account.controller.dto.request.SignUpRequest
import com.ieum.account.controller.dto.response.TokenResponse
import com.ieum.account.service.AccountService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
	private val accountService: AccountService,
) {

	@PostMapping("/sign-up")
	@ResponseStatus(HttpStatus.CREATED)
	fun signUp(
		@Valid @RequestBody request: SignUpRequest,
	) {
		accountService.signUp(request)
	}

	@PostMapping("/login")
	fun login(
		@Valid @RequestBody request: LoginRequest,
	): TokenResponse {
		return accountService.login(request)
	}

	@PostMapping("/refresh")
	fun reissue(
		@Valid @RequestBody request: ReissueRequest,
	): TokenResponse {
		return accountService.reissue(request.refreshToken)
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun logout(
		@Valid @RequestBody request: ReissueRequest,
	) {
		accountService.logout(request.refreshToken)
	}
}
