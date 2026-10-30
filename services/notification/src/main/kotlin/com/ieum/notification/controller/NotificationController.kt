package com.ieum.notification.controller

import com.ieum.notification.domain.enums.NotificationTab
import com.ieum.notification.service.NotificationInboxService
import com.ieum.notification.service.delivery.SseEmitterRegistry
import com.ieum.web.response.BaseResponseEntity
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notification")
class NotificationController(
	private val notificationInboxService: NotificationInboxService,
	private val sseEmitterRegistry: SseEmitterRegistry,
) {

	@GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
	fun stream(@AuthenticationPrincipal jwt: Jwt): SseEmitter {
		return sseEmitterRegistry.connect(UUID.fromString(jwt.subject))
	}

	@GetMapping
	fun getNotifications(
		@AuthenticationPrincipal jwt: Jwt,
		@RequestParam tab: NotificationTab,
		@RequestParam(required = false) familyId: UUID?,
		@RequestParam(defaultValue = "1") @Min(1) page: Int,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
	): BaseResponseEntity {
		val notifications = notificationInboxService.getNotifications(requireNotNull(jwt.subject), tab, familyId, page, size)
		return BaseResponseEntity.page(notifications.content, notifications.totalElements, page, size)
	}

	@GetMapping("/unread-count")
	fun getUnreadCount(
		@AuthenticationPrincipal jwt: Jwt,
		@RequestParam tab: NotificationTab,
		@RequestParam(required = false) familyId: UUID?,
	): BaseResponseEntity {
		return BaseResponseEntity.single(notificationInboxService.getUnreadCount(requireNotNull(jwt.subject), tab, familyId))
	}

	@PostMapping("/{notificationId}/read")
	fun read(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable notificationId: UUID,
	): BaseResponseEntity {
		notificationInboxService.updateRead(requireNotNull(jwt.subject), notificationId, true)
		return BaseResponseEntity.emptyNoContent()
	}

	@DeleteMapping("/{notificationId}/read")
	fun unread(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable notificationId: UUID,
	): BaseResponseEntity {
		notificationInboxService.updateRead(requireNotNull(jwt.subject), notificationId, false)
		return BaseResponseEntity.emptyNoContent()
	}

	@PostMapping("/read-all")
	fun readAll(
		@AuthenticationPrincipal jwt: Jwt,
		@RequestParam tab: NotificationTab,
		@RequestParam(required = false) familyId: UUID?,
	): BaseResponseEntity {
		notificationInboxService.readAll(requireNotNull(jwt.subject), tab, familyId)
		return BaseResponseEntity.emptyNoContent()
	}

	@DeleteMapping("/{notificationId}")
	fun deleteNotification(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable notificationId: UUID,
	): BaseResponseEntity {
		notificationInboxService.deleteNotification(requireNotNull(jwt.subject), notificationId)
		return BaseResponseEntity.emptyNoContent()
	}
}
