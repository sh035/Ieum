package com.ieum.notification.controller.dto.request

import jakarta.validation.constraints.NotBlank

data class UnregisterDeviceTokenRequest(
	@field:NotBlank
	val token: String,
)
