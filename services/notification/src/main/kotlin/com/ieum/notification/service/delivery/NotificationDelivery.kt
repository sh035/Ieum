package com.ieum.notification.service.delivery

import com.ieum.notification.controller.dto.response.NotificationResponse
import java.util.UUID

// Pod 간 SSE 브로드캐스트 메시지.
data class NotificationDelivery(
	val accountId: UUID,
	val notification: NotificationResponse,
)
