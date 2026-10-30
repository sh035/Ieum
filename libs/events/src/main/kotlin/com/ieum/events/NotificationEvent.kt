package com.ieum.events

// 서비스 안에서 커밋 후 Kafka 로 옮기기 위한 애플리케이션 이벤트.
data class NotificationEvent(
	val type: NotificationEventType,
	val data: NotificationEventData,
)
