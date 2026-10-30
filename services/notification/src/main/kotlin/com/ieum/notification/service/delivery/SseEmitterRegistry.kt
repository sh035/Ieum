package com.ieum.notification.service.delivery

import org.slf4j.LoggerFactory
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// 이 Pod 에 붙은 SSE 연결만 들고 있다. 한 계정이 여러 기기로 붙을 수 있다.
@Component
class SseEmitterRegistry {

	private val log = LoggerFactory.getLogger(javaClass)
	private val emitters = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>()

	fun connect(accountId: UUID): SseEmitter {
		val emitter = SseEmitter(TIMEOUT_MILLIS)
		emitters.compute(accountId) { _, list -> (list ?: CopyOnWriteArrayList()).apply { add(emitter) } }
		emitter.onCompletion { remove(accountId, emitter) }
		emitter.onTimeout { emitter.complete() }
		emitter.onError { remove(accountId, emitter) }
		// 첫 바이트를 바로 보내야 프록시 / 클라이언트가 연결이 열린 걸 안다.
		emitter.send(SseEmitter.event().comment("connected"))
		return emitter
	}

	// 응답 대기 중인 연결이 graceful shutdown 을 붙잡지 않게 먼저 닫는다. 앱은 다른 Pod 로 다시 붙는다.
	@EventListener(ContextClosedEvent::class)
	fun completeAll() {
		emitters.values.flatten().forEach { it.complete() }
	}

	fun send(accountId: UUID, name: String, data: Any) {
		emitters[accountId]?.forEach { emitter -> trySend(accountId, emitter, SseEmitter.event().name(name).data(data)) }
	}

	// 끊긴 연결을 일찍 찾고, 중간 프록시가 유휴 연결을 끊지 않게 한다.
	@Scheduled(fixedRate = HEARTBEAT_MILLIS)
	fun heartbeat() {
		emitters.forEach { (accountId, list) -> list.forEach { trySend(accountId, it, SseEmitter.event().comment("heartbeat")) } }
	}

	private fun trySend(accountId: UUID, emitter: SseEmitter, event: SseEmitter.SseEventBuilder) {
		try {
			emitter.send(event)
		} catch (e: Exception) {
			log.debug("SSE send failed, removing emitter. accountId={}, message={}", accountId, e.message)
			remove(accountId, emitter)
		}
	}

	private fun remove(accountId: UUID, emitter: SseEmitter) {
		emitters.computeIfPresent(accountId) { _, list -> list.apply { remove(emitter) }.ifEmpty { null } }
	}

	companion object {
		private const val TIMEOUT_MILLIS = 30 * 60 * 1000L
		private const val HEARTBEAT_MILLIS = 30_000L
	}
}
