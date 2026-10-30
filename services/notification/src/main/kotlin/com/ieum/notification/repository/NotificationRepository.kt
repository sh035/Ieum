package com.ieum.notification.repository

import com.ieum.notification.domain.entity.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

	fun existsByAccountIdAndDedupKey(accountId: UUID, dedupKey: String): Boolean

	fun findByNotificationIdAndAccountIdAndIsDeletedFalse(notificationId: UUID, accountId: UUID): Notification?
}
