package com.ieum.account.service

import com.ieum.events.AccountEventData
import com.ieum.events.AccountEventType
import com.ieum.events.IeumEvent
import com.ieum.events.Topics
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.UUID

// 커밋된 탈퇴만 알리도록 트랜잭션 안에서 쌓고 커밋 후 Kafka 로 보낸다.
// 전송이 실패하면 notification 의 기기 토큰 / 알림함이 남는다. 브리핑은 탈퇴한 계정을 걸러서 보내지 않는다. 정리할 서비스가 늘면 outbox 로 바꾼다.
@Component
class AccountEventPublisher(
	private val applicationEventPublisher: ApplicationEventPublisher,
	private val kafkaTemplate: KafkaTemplate<String, String>,
	private val objectMapper: ObjectMapper,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	data class AccountEvent(val type: AccountEventType, val data: AccountEventData)

	fun publish(type: AccountEventType, data: AccountEventData) {
		check(TransactionSynchronizationManager.isActualTransactionActive()) {
			"Account event must be published inside a transaction. type=$type"
		}
		applicationEventPublisher.publishEvent(AccountEvent(type, data))
	}

	// 전송 실패가 이미 커밋된 탈퇴 응답을 깨면 안 되므로 로그만 남긴다.
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	fun relay(event: AccountEvent) {
		try {
			val message = objectMapper.writeValueAsString(IeumEvent(UUID.randomUUID(), event.type.name, LocalDateTime.now(), event.data))
			kafkaTemplate.send(Topics.ACCOUNT, event.data.accountId.toString(), message)
				?.whenComplete { _, e -> e?.let { log.error("Account event send failed. type={}, accountId={}", event.type, event.data.accountId, it) } }
		} catch (e: Exception) {
			log.error("Account event send failed. type={}, accountId={}", event.type, event.data.accountId, e)
		}
	}
}
