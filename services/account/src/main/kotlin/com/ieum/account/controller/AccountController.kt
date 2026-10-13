package com.ieum.account.controller

import com.ieum.account.service.AccountService
import com.ieum.web.response.BaseResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/account")
class AccountController(
	private val accountService: AccountService,
) {

	@GetMapping("/me")
	fun getMe(
		@AuthenticationPrincipal jwt: Jwt,
	): BaseResponseEntity {
		return BaseResponseEntity.single(accountService.getAccount(requireNotNull(jwt.subject)))
	}
}
