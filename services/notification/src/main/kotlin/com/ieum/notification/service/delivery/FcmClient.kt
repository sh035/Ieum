package com.ieum.notification.service.delivery

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import java.util.Base64
import com.google.firebase.messaging.Notification as FcmNotification

// Firebase 메시지 객체는 내용을 꺼내 볼 수 없어서, 테스트가 정확한 값으로 검증할 수 있게 이 경계에서 감싼다.
@Component
@ConditionalOnExpression(FcmClient.ENABLED)
class FcmClient(
	@Value("\${firebase.credentials}") credentials: String,
) {

	// 서비스 계정 JSON 을 base64 로 받는다 (JWT 키와 같은 방식).
	private val messaging: FirebaseMessaging = Base64.getDecoder().decode(credentials).inputStream().use { json ->
		val options = FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(json)).build()
		FirebaseMessaging.getInstance(FirebaseApp.initializeApp(options))
	}

	// 다시 보내도 소용없는 토큰 (앱 삭제 / 잘못된 토큰) 을 돌려준다.
	fun send(tokens: Set<String>, title: String, body: String, data: Map<String, String>): Set<String> {
		val ordered = tokens.toList()
		val message = MulticastMessage.builder()
			.addAllTokens(ordered)
			.setNotification(FcmNotification.builder().setTitle(title).setBody(body).build())
			.putAllData(data)
			.build()

		return messaging.sendEachForMulticast(message).responses
			.zip(ordered)
			.filter { (response, _) -> response.exception?.messagingErrorCode in INVALID_TOKEN_CODES }
			.map { (_, token) -> token }
			.toSet()
	}

	companion object {
		// 키가 비어 있는 로컬 / 테스트에서는 FCM 빈을 만들지 않는다.
		const val ENABLED = "'\${firebase.credentials:}' != ''"
		private val INVALID_TOKEN_CODES = setOf(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT)
	}
}
