package com.ieum.family.controller.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateFamilyMemberNicknameRequest(
	@NotBlank(message = "Nickname is required.")
	@Size(max = 20, message = "Nickname must not exceed 20 characters.")
	val nickname: String,
)
