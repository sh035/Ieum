package com.ieum.notification.service

import com.ieum.events.IeumEvent
import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventType
import com.ieum.grpc.client.account.AccountClient
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.notification.domain.entity.Notification
import com.ieum.notification.repository.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class NotificationService(
	private val notificationRepository: NotificationRepository,
	private val composer: NotificationMessageComposer,
	private val accountClient: AccountClient,
	private val familyClient: FamilyClient,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// Kafka 는 같은 메시지를 다시 줄 수 있어서 받는 사람별 dedupKey 로 한 번만 저장한다.
	fun receive(event: IeumEvent<NotificationEventData>) {
		val type = NotificationEventType.valueOf(event.eventType)
		val data = event.data
		val dedupKey = data.dedupKey ?: event.eventId.toString()

		val nickname = data.actorAccountId?.let { accountClient.getNicknames(setOf(it))[it] }
		val familyName = data.familyId
			?.takeIf { type == NotificationEventType.FAMILY_SCHEDULE_CREATED }
			?.let { familyClient.getFamily(it)?.name }
		val message = composer.compose(type, data, nickname, familyName)

		data.recipientAccountIds.distinct()
			.filterNot { notificationRepository.existsByAccountIdAndDedupKey(it, dedupKey) }
			.forEach { accountId ->
				try {
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
					log.debug("Duplicate notification skipped. accountId={}, dedupKey={}", accountId, dedupKey)
				}
			}
	}
}
