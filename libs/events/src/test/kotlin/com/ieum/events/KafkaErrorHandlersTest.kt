package com.ieum.events

import org.junit.jupiter.api.Test
import org.springframework.util.backoff.BackOffExecution
import kotlin.test.assertEquals

class KafkaErrorHandlersTest {

	@Test
	fun `가족 이벤트는 1분 3분 10분 뒤에 다시 시도하고 멈춘다`() {
		val execution = FAMILY_EVENT_BACK_OFF.start()

		assertEquals(listOf(60_000L, 180_000L, 600_000L, BackOffExecution.STOP), List(4) { execution.nextBackOff() })
	}
}
