package com.ieum.account.controller.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdatePasswordRequest(
	@NotBlank(message = "Current password is required.")
	val currentPassword: String,

	@NotBlank(message = "Password is required.")
	@Size(min = 8, max = 16, message = "Password must be between 8 and 16 characters.")
	@Pattern(
		regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
		message = "Password must contain letters, digits and special characters.",
	)
	val newPassword: String,
)
