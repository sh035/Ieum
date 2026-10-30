package com.ieum.notification.repository

import com.ieum.notification.domain.entity.Notification
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {

	fun existsByAccountIdAndDedupKey(accountId: UUID, dedupKey: String): Boolean
}
