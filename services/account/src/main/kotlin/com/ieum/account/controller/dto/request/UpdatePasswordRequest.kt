package com.ieum.account.controller.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdatePasswordRequest(
	@NotBlank(message = "Current password is required.")
	val currentPassword: String,

	@NotBlank(message = "Password is required.")
	@Size(min = PasswordPolicy.MIN, max = PasswordPolicy.MAX, message = PasswordPolicy.SIZE_MESSAGE)
	@Pattern(regexp = PasswordPolicy.REGEXP, message = PasswordPolicy.REGEXP_MESSAGE)
	val newPassword: String,
)
