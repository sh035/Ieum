package com.ieum.notification.controller.dto.request

import com.ieum.notification.domain.enums.DevicePlatform
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterDeviceTokenRequest(
	@field:NotBlank
	@field:Size(max = 500)
	val token: String,
	val platform: DevicePlatform,
)
