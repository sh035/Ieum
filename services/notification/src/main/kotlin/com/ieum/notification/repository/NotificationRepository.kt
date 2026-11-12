package com.ieum.notification.repository

import com.ieum.notification.domain.entity.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

	fun existsByAccountIdAndDedupKey(accountId: UUID, dedupKey: String): Boolean

	// 알림이 많을 수 있어 한 문장으로 지운다. @PreUpdate 를 거치지 않아 수정 시각과 수정자(시스템)를 직접 넣는다.
	@Modifying
	@Query("update Notification n set n.isDeleted = true, n.updatedAt = :now, n.updatedBy = null where n.accountId = :accountId and n.isDeleted = false")
	fun softDeleteAllByAccountId(accountId: UUID, now: LocalDateTime): Int

	fun findByNotificationIdAndAccountIdAndIsDeletedFalse(notificationId: UUID, accountId: UUID): Notification?
}
