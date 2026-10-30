package com.ieum.notification.controller.dto.response

import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import java.time.LocalDateTime
import java.util.UUID

data class NotificationResponse(
	val notificationId: UUID,
	val familyId: UUID?,
	val type: NotificationEventType,
	val title: String,
	val body: String,
	val targetType: NotificationTargetType?,
	val targetId: UUID?,
	val isRead: Boolean,
	val createdAt: LocalDateTime,
)
