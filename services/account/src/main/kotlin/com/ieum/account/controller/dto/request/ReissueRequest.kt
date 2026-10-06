package com.ieum.account.controller.dto.request

import jakarta.validation.constraints.NotBlank

data class ReissueRequest(
	@NotBlank(message = "Refresh token is required.")
	val refreshToken: String,
)
