package com.ieum.notification.service

import com.ieum.events.AccountEventData
import com.ieum.events.AccountEventType
import com.ieum.events.IeumEvent
import com.ieum.events.Topics
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@Component
class AccountEventReceiver(
	private val objectMapper: ObjectMapper,
	private val deviceTokenService: DeviceTokenService,
	private val notificationInboxService: NotificationInboxService,
) {

	// 실패하면 알림 이벤트와 같은 오류 처리기로 다시 시도한 뒤 ieum.notification.DLT 로 간다.
	@KafkaListener(topics = [Topics.ACCOUNT])
	fun receive(message: String) {
		val event = objectMapper.readValue<IeumEvent<AccountEventData>>(message)
		// 지금은 ACCOUNT_DELETED 하나뿐이다. 모르는 타입은 IllegalArgumentException 으로 바로 DLT 에 간다.
		AccountEventType.valueOf(event.eventType)

		deviceTokenService.deleteAll(event.data.accountId)
		notificationInboxService.deleteAll(event.data.accountId)
	}
}
