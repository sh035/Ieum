package com.ieum.account.controller.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateAccountRequest(
	@NotBlank(message = "Nickname is required.")
	@Size(max = 20, message = "Nickname must not exceed 20 characters.")
	val nickname: String,
)
