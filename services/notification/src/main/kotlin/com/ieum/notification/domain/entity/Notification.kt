package com.ieum.notification.domain.entity

import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.web.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.util.UUID

@Entity
@Table(name = "notification")
class Notification(
	@Column(nullable = false, updatable = false)
	val accountId: UUID,

	@Column(updatable = false)
	val familyId: UUID?,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	val type: NotificationEventType,

	@Column(nullable = false, updatable = false)
	val title: String,

	@Column(nullable = false, updatable = false)
	val body: String,

	@Enumerated(EnumType.STRING)
	@Column(updatable = false)
	val targetType: NotificationTargetType?,

	@Column(updatable = false)
	val targetId: UUID?,

	@Column(nullable = false, updatable = false)
	val dedupKey: String,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val notificationId: UUID? = null,
) : BaseEntity() {

	@Column(nullable = false)
	var isRead: Boolean = false
}
