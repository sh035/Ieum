package com.ieum.notification.controller

import com.ieum.notification.controller.dto.request.RegisterDeviceTokenRequest
import com.ieum.notification.controller.dto.request.UnregisterDeviceTokenRequest
import com.ieum.notification.service.DeviceTokenService
import com.ieum.web.response.BaseResponseEntity
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notification/device-token")
class DeviceTokenController(
	private val deviceTokenService: DeviceTokenService,
) {

	@PostMapping
	fun register(
		@AuthenticationPrincipal jwt: Jwt,
		@Valid @RequestBody request: RegisterDeviceTokenRequest,
	): BaseResponseEntity {
		deviceTokenService.register(requireNotNull(jwt.subject), request)
		return BaseResponseEntity.emptyNoContent()
	}

	@DeleteMapping
	fun unregister(
		@AuthenticationPrincipal jwt: Jwt,
		@Valid @RequestBody request: UnregisterDeviceTokenRequest,
	): BaseResponseEntity {
		deviceTokenService.unregister(requireNotNull(jwt.subject), request.token)
		return BaseResponseEntity.emptyNoContent()
	}
}
