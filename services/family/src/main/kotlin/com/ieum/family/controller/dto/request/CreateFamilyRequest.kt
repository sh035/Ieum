package com.ieum.family.controller.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateFamilyRequest(
	@NotBlank(message = "Family name is required.")
	@Size(max = 20, message = "Family name must not exceed 20 characters.")
	val name: String,
)
