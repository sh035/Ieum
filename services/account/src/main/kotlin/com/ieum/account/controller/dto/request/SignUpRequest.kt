package com.ieum.account.controller.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class SignUpRequest(
	@NotBlank(message = "Email is required.")
	@Pattern(
		regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
		message = "Email format is invalid.",
	)
	val email: String,

	@NotBlank(message = "Password is required.")
	@Size(min = PasswordPolicy.MIN, max = PasswordPolicy.MAX, message = PasswordPolicy.SIZE_MESSAGE)
	@Pattern(regexp = PasswordPolicy.REGEXP, message = PasswordPolicy.REGEXP_MESSAGE)
	val password: String,

	@NotBlank(message = "Nickname is required.")
	@Size(max = 20, message = "Nickname must not exceed 20 characters.")
	val nickname: String,

	@Past(message = "Birth date must be in the past.")
	val birthDate: LocalDate,
)
