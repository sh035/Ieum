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
import org.springframework.data.jpa.domain.PredicateSpecification
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.domain.UpdateSpecification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
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

		return notificationRepository.findAll(Specification.where(inTab(me, tab, familyId)), pageable).map(NotificationResponse::from)
	}

	fun getUnreadCount(accountId: String, tab: NotificationTab, familyId: UUID?): UnreadCountResponse {
		val spec = inTab(UUID.fromString(accountId), tab, familyId).and(unread())

		return UnreadCountResponse(notificationRepository.count(spec))
	}

	fun updateRead(accountId: String, notificationId: UUID, isRead: Boolean) {
		val notification = findMine(UUID.fromString(accountId), notificationId)
		notification.isRead = isRead
		notificationRepository.save(notification)
	}

	// 한 번의 UPDATE 로 처리해서 엔티티 @PreUpdate 를 거치지 않으니 updatedAt 을 직접 넣는다.
	@Transactional
	fun readAll(accountId: String, tab: NotificationTab, familyId: UUID?) {
		val where = inTab(UUID.fromString(accountId), tab, familyId).and(unread())

		notificationRepository.update(
			UpdateSpecification.update<Notification> { _, update, _ ->
				update.set("isRead", true)
				update.set("updatedAt", LocalDateTime.now())
			}.where(where),
		)
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

	private fun inTab(me: UUID, tab: NotificationTab, familyId: UUID?): PredicateSpecification<Notification> {
		val mine = PredicateSpecification<Notification> { root, cb ->
			cb.and(cb.equal(root.get<UUID>("accountId"), me), cb.isFalse(root.get("isDeleted")))
		}

		return when (tab) {
			NotificationTab.ALL -> mine
			NotificationTab.FAMILY -> {
				val checked = checkFamily(me, familyId)
				mine.and { root, cb -> cb.equal(root.get<UUID>("familyId"), checked) }
			}
			NotificationTab.PERSONAL -> mine.and { root, cb -> cb.isNull(root.get<UUID>("familyId")) }
		}
	}

	private fun unread() = PredicateSpecification<Notification> { root, cb -> cb.isFalse(root.get("isRead")) }

	private fun checkFamily(me: UUID, familyId: UUID?): UUID {
		if (familyId == null) {
			throw IeumException(NotificationErrorCode.FAMILY_ID_REQUIRED)
		}
		if (!familyClient.isMember(familyId, me)) {
			throw IeumException(NotificationErrorCode.FAMILY_NOT_FOUND)
		}
		return familyId
	}
}
