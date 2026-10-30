package com.ieum.notification.controller.dto.response

import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.notification.domain.entity.Notification
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
) {
	companion object {
		fun from(notification: Notification) = NotificationResponse(
			notificationId = requireNotNull(notification.notificationId),
			familyId = notification.familyId,
			type = notification.type,
			title = notification.title,
			body = notification.body,
			targetType = notification.targetType,
			targetId = notification.targetId,
			isRead = notification.isRead,
			createdAt = notification.createdAt,
		)
	}
}
