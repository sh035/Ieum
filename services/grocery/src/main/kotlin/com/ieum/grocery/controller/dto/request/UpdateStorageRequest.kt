package com.ieum.grocery.controller.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateStorageRequest(
	@field:NotBlank
	@field:Size(max = 50)
	val name: String,
)
