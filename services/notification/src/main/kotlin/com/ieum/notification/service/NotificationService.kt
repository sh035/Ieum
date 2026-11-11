package com.ieum.notification.service

import com.ieum.events.IeumEvent
import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.grpc.client.account.AccountClient
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.notification.domain.entity.Notification
import com.ieum.notification.repository.NotificationRepository
import com.ieum.notification.service.delivery.NotificationSender
import com.ieum.web.exception.IeumException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.sql.SQLException
import java.util.UUID

@Service
class NotificationService(
	private val notificationRepository: NotificationRepository,
	private val composer: NotificationMessageComposer,
	private val accountClient: AccountClient,
	private val familyClient: FamilyClient,
	private val senders: List<NotificationSender>,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// Kafka 는 같은 메시지를 다시 줄 수 있어서 받는 사람별 dedupKey 로 한 번만 저장한다.
	fun receive(event: IeumEvent<NotificationEventData>) {
		val type = NotificationEventType.valueOf(event.eventType)
		val data = event.data
		val dedupKey = data.dedupKey ?: event.eventId.toString()
		val recipients = data.recipientAccountIds.distinct().filterNot { notificationRepository.existsByAccountIdAndDedupKey(it, dedupKey) }
		if (recipients.isEmpty()) {
			return
		}

		// 그 가족에서 쓰는 닉네임이 먼저다. 구성원이었던 적 없는 사람(처음 초대받아 거절한 사람 등)은 계정 닉네임이다.
		val nickname = data.actorAccountId?.let { actor ->
			data.familyId?.let { familyId -> lookup { familyClient.getNicknames(familyId, setOf(actor))[actor] } }
				?: lookup { accountClient.getNicknames(setOf(actor))[actor] }
		}
		val familyName = data.familyId
			?.takeIf { type in FAMILY_NAME_TYPES }
			?.let { familyId -> lookup { familyClient.getFamily(familyId)?.name } }
		val message = composer.compose(type, data, nickname, familyName)
		// 받는 사람이 그 가족 구성원이 아닌 알림은 가족 탭에 열 수 없어서 개인 알림으로 저장한다.
		val inboxFamilyId = data.familyId.takeUnless { type in NON_MEMBER_TYPES }

		recipients.forEach { accountId ->
			saveAndDeliver(accountId, type, message, dedupKey, inboxFamilyId, data.targetType, data.targetId)
		}
	}

	// 동시에 같은 알림이 들어온 경우만 건너뛰고, 길이 초과 같은 다른 위반은 호출한 쪽으로 던진다.
	fun saveAndDeliver(
		accountId: UUID,
		type: NotificationEventType,
		message: NotificationMessage,
		dedupKey: String,
		familyId: UUID? = null,
		targetType: NotificationTargetType? = null,
		targetId: UUID? = null,
	) {
		val saved = try {
			notificationRepository.saveAndFlush(
				Notification(
					accountId = accountId,
					familyId = familyId,
					type = type,
					title = message.title,
					body = message.body,
					targetType = targetType,
					targetId = targetId,
					dedupKey = dedupKey,
				),
			)
		} catch (e: DataIntegrityViolationException) {
			if ((e.mostSpecificCause as? SQLException)?.sqlState != UNIQUE_VIOLATION) {
				throw e
			}
			log.debug("Duplicate notification skipped. accountId={}, dedupKey={}", accountId, dedupKey)
			return
		}
		deliver(saved)
	}

	// 전달 실패는 저장된 알림을 되돌릴 이유가 아니라서, sender 마다 따로 잡고 다음으로 넘어간다.
	private fun deliver(notification: Notification) {
		senders.forEach { sender ->
			try {
				sender.send(notification)
			} catch (e: Exception) {
				log.warn("Notification delivery failed. sender={}, notificationId={}", sender.javaClass.simpleName, notification.notificationId, e)
			}
		}
	}

	// 닉네임과 가족 이름은 문구 장식이라, 조회가 실패해도 알림은 대체 문구로 저장한다.
	private fun lookup(block: () -> String?): String? =
		try {
			block()
		} catch (e: IeumException) {
			log.warn("Notification name lookup failed. message={}", e.message)
			null
		}

	companion object {
		private const val UNIQUE_VIOLATION = "23505"

		private val NON_MEMBER_TYPES = setOf(NotificationEventType.FAMILY_INVITED, NotificationEventType.FAMILY_MEMBER_REMOVED)

		// 문구에 가족 이름을 넣는 알림만 family 를 조회한다.
		private val FAMILY_NAME_TYPES = setOf(
			NotificationEventType.FAMILY_SCHEDULE_CREATED,
			NotificationEventType.FAMILY_INVITED,
			NotificationEventType.FAMILY_INVITATION_DECLINED,
			NotificationEventType.FAMILY_MEMBER_REMOVED,
			NotificationEventType.FAMILY_OWNERSHIP_TRANSFERRED,
		)
	}
}
