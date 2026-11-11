package com.ieum.account.controller.dto.request

import jakarta.validation.constraints.NotBlank

data class WithdrawRequest(
	@NotBlank(message = "Password is required.")
	val password: String,

	// 혼자인 가족을 탈퇴와 함께 지우는 데 동의했는지다.
	val deleteSoloFamilies: Boolean = false,
)
