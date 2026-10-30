package com.ieum.notification.service.delivery

import com.ieum.notification.domain.entity.Notification

// 저장한 알림을 앱으로 보내는 경로 (SSE / FCM).
interface NotificationSender {
	fun send(notification: Notification)
}
