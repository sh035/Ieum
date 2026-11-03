package com.ieum.family.domain.entity

import com.ieum.events.FamilyEventType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDateTime
import java.util.UUID

// 다른 서비스로 보낼 가족 이벤트다. 인프라 테이블이라 공통 컬럼을 두지 않는다.
@Entity
@Table(name = "outbox_event")
class OutboxEvent(
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	val eventType: FamilyEventType,

	@Column(nullable = false, updatable = false)
	val familyId: UUID,

	@Column(nullable = false, updatable = false)
	val payload: String,

	@Column(nullable = false, updatable = false)
	val createdAt: LocalDateTime,

	var sentAt: LocalDateTime? = null,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val outboxEventId: UUID? = null,
)
