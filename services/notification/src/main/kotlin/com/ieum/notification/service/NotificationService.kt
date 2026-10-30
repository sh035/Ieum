package com.ieum.notification.service

import com.ieum.events.IeumEvent
import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventType
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

		val nickname = data.actorAccountId?.let { actor -> lookup { accountClient.getNicknames(setOf(actor))[actor] } }
		val familyName = data.familyId
			?.takeIf { type == NotificationEventType.FAMILY_SCHEDULE_CREATED }
			?.let { familyId -> lookup { familyClient.getFamily(familyId)?.name } }
		val message = composer.compose(type, data, nickname, familyName)

		recipients.forEach { accountId ->
			val saved = try {
				notificationRepository.saveAndFlush(
					Notification(
						accountId = accountId,
						familyId = data.familyId,
						type = type,
						title = message.title,
						body = message.body,
						targetType = data.targetType,
						targetId = data.targetId,
						dedupKey = dedupKey,
					),
				)
			} catch (e: DataIntegrityViolationException) {
				// 동시에 같은 알림이 들어온 경우만 건너뛴다. 길이 초과 같은 다른 위반은 재시도 후 DLT 로 보낸다.
				if ((e.mostSpecificCause as? SQLException)?.sqlState != UNIQUE_VIOLATION) {
					throw e
				}
				log.debug("Duplicate notification skipped. accountId={}, dedupKey={}", accountId, dedupKey)
				return@forEach
			}
			deliver(saved)
		}
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
	}
}
