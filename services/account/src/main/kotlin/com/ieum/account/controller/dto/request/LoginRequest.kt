package com.ieum.account.controller.dto.request

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
	@NotBlank(message = "Email is required.")
	val email: String,

	@NotBlank(message = "Password is required.")
	val password: String,
)
