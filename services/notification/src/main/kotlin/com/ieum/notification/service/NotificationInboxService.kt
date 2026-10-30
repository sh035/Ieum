package com.ieum.notification.service

import com.ieum.grpc.client.family.FamilyClient
import com.ieum.notification.controller.dto.response.NotificationResponse
import com.ieum.notification.controller.dto.response.UnreadCountResponse
import com.ieum.notification.domain.entity.Notification
import com.ieum.notification.domain.enums.NotificationTab
import com.ieum.notification.exception.NotificationErrorCode
import com.ieum.notification.repository.NotificationRepository
import com.ieum.web.exception.IeumException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationInboxService(
	private val notificationRepository: NotificationRepository,
	private val familyClient: FamilyClient,
) {

	@Transactional(readOnly = true)
	fun getNotifications(accountId: String, tab: NotificationTab, familyId: UUID?, page: Int, size: Int): Page<NotificationResponse> {
		val me = UUID.fromString(accountId)
		// 같은 시각이면 시간순 UUID(v7) 로 순서를 고정한다.
		val pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("notificationId")))

		val notifications = when (tab) {
			NotificationTab.ALL -> notificationRepository.findAllByAccountIdAndIsDeletedFalse(me, pageable)
			NotificationTab.FAMILY ->
				notificationRepository.findAllByAccountIdAndFamilyIdAndIsDeletedFalse(me, checkFamily(me, familyId), pageable)
			NotificationTab.PERSONAL -> notificationRepository.findAllByAccountIdAndFamilyIdIsNullAndIsDeletedFalse(me, pageable)
		}

		return notifications.map { it.toResponse() }
	}

	fun getUnreadCount(accountId: String, tab: NotificationTab, familyId: UUID?): UnreadCountResponse {
		val me = UUID.fromString(accountId)

		val count = when (tab) {
			NotificationTab.ALL -> notificationRepository.countByAccountIdAndIsReadFalseAndIsDeletedFalse(me)
			NotificationTab.FAMILY ->
				notificationRepository.countByAccountIdAndFamilyIdAndIsReadFalseAndIsDeletedFalse(me, checkFamily(me, familyId))
			NotificationTab.PERSONAL -> notificationRepository.countByAccountIdAndFamilyIdIsNullAndIsReadFalseAndIsDeletedFalse(me)
		}

		return UnreadCountResponse(count)
	}

	fun updateRead(accountId: String, notificationId: UUID, isRead: Boolean) {
		val notification = findMine(UUID.fromString(accountId), notificationId)
		notification.isRead = isRead
		notificationRepository.save(notification)
	}

	@Transactional
	fun readAll(accountId: String, tab: NotificationTab, familyId: UUID?) {
		val me = UUID.fromString(accountId)

		val unread = when (tab) {
			NotificationTab.ALL -> notificationRepository.findAllByAccountIdAndIsReadFalseAndIsDeletedFalse(me)
			NotificationTab.FAMILY ->
				notificationRepository.findAllByAccountIdAndFamilyIdAndIsReadFalseAndIsDeletedFalse(me, checkFamily(me, familyId))
			NotificationTab.PERSONAL -> notificationRepository.findAllByAccountIdAndFamilyIdIsNullAndIsReadFalseAndIsDeletedFalse(me)
		}
		unread.forEach { it.isRead = true }
		notificationRepository.saveAll(unread)
	}

	fun deleteNotification(accountId: String, notificationId: UUID) {
		val notification = findMine(UUID.fromString(accountId), notificationId)
		notification.delete()
		notificationRepository.save(notification)
	}

	// 남의 알림은 존재 자체를 숨긴다.
	private fun findMine(me: UUID, notificationId: UUID): Notification =
		notificationRepository.findByNotificationIdAndAccountIdAndIsDeletedFalse(notificationId, me)
			?: throw IeumException(NotificationErrorCode.NOTIFICATION_NOT_FOUND)

	private fun checkFamily(me: UUID, familyId: UUID?): UUID {
		if (familyId == null) {
			throw IeumException(NotificationErrorCode.FAMILY_ID_REQUIRED)
		}
		if (!familyClient.isMember(familyId, me)) {
			throw IeumException(NotificationErrorCode.FAMILY_NOT_FOUND)
		}
		return familyId
	}

	private fun Notification.toResponse() = NotificationResponse(
		notificationId = requireNotNull(notificationId),
		familyId = familyId,
		type = type,
		title = title,
		body = body,
		targetType = targetType,
		targetId = targetId,
		isRead = isRead,
		createdAt = createdAt,
	)
}
