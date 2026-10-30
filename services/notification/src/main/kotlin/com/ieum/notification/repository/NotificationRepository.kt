package com.ieum.notification.repository

import com.ieum.notification.domain.entity.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {

	fun existsByAccountIdAndDedupKey(accountId: UUID, dedupKey: String): Boolean

	fun findByNotificationIdAndAccountIdAndIsDeletedFalse(notificationId: UUID, accountId: UUID): Notification?

	fun findAllByAccountIdAndIsDeletedFalse(accountId: UUID, pageable: Pageable): Page<Notification>

	fun findAllByAccountIdAndFamilyIdAndIsDeletedFalse(accountId: UUID, familyId: UUID, pageable: Pageable): Page<Notification>

	fun findAllByAccountIdAndFamilyIdIsNullAndIsDeletedFalse(accountId: UUID, pageable: Pageable): Page<Notification>

	fun findAllByAccountIdAndIsReadFalseAndIsDeletedFalse(accountId: UUID): List<Notification>

	fun findAllByAccountIdAndFamilyIdAndIsReadFalseAndIsDeletedFalse(accountId: UUID, familyId: UUID): List<Notification>

	fun findAllByAccountIdAndFamilyIdIsNullAndIsReadFalseAndIsDeletedFalse(accountId: UUID): List<Notification>

	fun countByAccountIdAndIsReadFalseAndIsDeletedFalse(accountId: UUID): Long

	fun countByAccountIdAndFamilyIdAndIsReadFalseAndIsDeletedFalse(accountId: UUID, familyId: UUID): Long

	fun countByAccountIdAndFamilyIdIsNullAndIsReadFalseAndIsDeletedFalse(accountId: UUID): Long
}
