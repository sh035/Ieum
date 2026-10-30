package com.ieum.events

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager

// 커밋된 변경만 알림이 가도록 트랜잭션 안에서 이벤트를 쌓고, 커밋 후 NotificationEventRelay 가 Kafka 로 보낸다.
@Component
class NotificationEventPublisher(
	private val applicationEventPublisher: ApplicationEventPublisher,
) {

	// 행동한 본인은 받는 사람에서 뺀다.
	fun publish(type: NotificationEventType, data: NotificationEventData) {
		// 트랜잭션 밖이면 AFTER_COMMIT 리스너가 오류 없이 이벤트를 버리므로, 발행 메서드의 @Transactional 누락을 바로 드러낸다.
		check(TransactionSynchronizationManager.isActualTransactionActive()) {
			"Notification event must be published inside a transaction. type=$type"
		}

		val recipients = data.recipientAccountIds.distinct() - setOfNotNull(data.actorAccountId)
		if (recipients.isEmpty()) {
			return
		}
		applicationEventPublisher.publishEvent(NotificationEvent(type, data.copy(recipientAccountIds = recipients)))
	}
}
