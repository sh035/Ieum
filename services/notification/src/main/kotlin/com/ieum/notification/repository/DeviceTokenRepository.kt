package com.ieum.notification.repository

import com.ieum.notification.domain.entity.DeviceToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DeviceTokenRepository : JpaRepository<DeviceToken, UUID> {

	fun findByTokenAndIsDeletedFalse(token: String): DeviceToken?

	fun findAllByAccountIdAndIsDeletedFalse(accountId: UUID): List<DeviceToken>
}
