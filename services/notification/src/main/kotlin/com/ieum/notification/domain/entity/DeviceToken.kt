package com.ieum.notification.domain.entity

import com.ieum.notification.domain.enums.DevicePlatform
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
@Table(name = "device_token")
class DeviceToken(
	@Column(nullable = false)
	var accountId: UUID,

	@Column(nullable = false, updatable = false)
	val token: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var platform: DevicePlatform,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val deviceTokenId: UUID? = null,
) : BaseEntity()
