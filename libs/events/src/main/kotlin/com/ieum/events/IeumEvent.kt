package com.ieum.events

import java.time.LocalDateTime
import java.util.UUID

// 서비스 간 이벤트 공통 형식 (컨벤션 28). Kafka 에는 문자열 JSON 으로 싣는다.
data class IeumEvent<T>(
	val eventId: UUID,
	val eventType: String,
	val createdAt: LocalDateTime,
	val data: T,
)
