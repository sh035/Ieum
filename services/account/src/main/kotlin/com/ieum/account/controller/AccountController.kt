package com.ieum.account.controller

import com.ieum.account.controller.dto.request.UpdateAccountRequest
import com.ieum.account.controller.dto.request.WithdrawRequest
import com.ieum.account.service.AccountService
import com.ieum.web.response.BaseResponseEntity
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

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

	@PatchMapping("/me")
	fun updateMe(
		@AuthenticationPrincipal jwt: Jwt,
		@Valid @RequestBody request: UpdateAccountRequest,
	): BaseResponseEntity {
		return BaseResponseEntity.single(accountService.updateAccount(requireNotNull(jwt.subject), request))
	}

	@GetMapping("/me/withdrawal")
	fun checkWithdrawal(
		@AuthenticationPrincipal jwt: Jwt,
	): BaseResponseEntity {
		return BaseResponseEntity.single(accountService.checkWithdrawal(requireNotNull(jwt.subject)))
	}

	@DeleteMapping("/me")
	fun withdraw(
		@AuthenticationPrincipal jwt: Jwt,
		@Valid @RequestBody request: WithdrawRequest,
	): BaseResponseEntity {
		accountService.withdraw(requireNotNull(jwt.subject), request)
		return BaseResponseEntity.emptyNoContent()
	}

	@GetMapping
	fun getProfiles(
		@RequestParam @Size(max = 100) ids: List<UUID>,
	): BaseResponseEntity {
		return BaseResponseEntity.single(accountService.getProfiles(ids))
	}
}
