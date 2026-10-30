package com.ieum.notification

import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.SendResponse
import com.ieum.notification.service.delivery.FcmClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals

class FcmClientTest {

	private fun success(): SendResponse = mock(SendResponse::class.java).also { `when`(it.isSuccessful).thenReturn(true) }

	private fun failure(code: MessagingErrorCode): SendResponse {
		val exception = mock(FirebaseMessagingException::class.java).also { `when`(it.messagingErrorCode).thenReturn(code) }
		return mock(SendResponse::class.java).also {
			`when`(it.isSuccessful).thenReturn(false)
			`when`(it.exception).thenReturn(exception)
		}
	}

	@Test
	fun `응답 순서대로 토큰과 짝지어 다시 보내도 소용없는 토큰만 고른다`() {
		val invalid = FcmClient.invalidTokens(
			listOf("uninstalled", "ok", "broken", "busy", "ok2"),
			listOf(
				failure(MessagingErrorCode.UNREGISTERED),
				success(),
				failure(MessagingErrorCode.INVALID_ARGUMENT),
				failure(MessagingErrorCode.UNAVAILABLE),
				success(),
			),
		)

		assertEquals(setOf("uninstalled", "broken"), invalid)
	}
}
