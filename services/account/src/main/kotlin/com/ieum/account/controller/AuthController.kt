package com.ieum.account.controller

import com.ieum.account.controller.dto.request.SignUpRequest
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
}
