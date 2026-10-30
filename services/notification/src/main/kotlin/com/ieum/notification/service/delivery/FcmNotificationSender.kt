package com.ieum.notification.service.delivery

import com.ieum.notification.domain.entity.Notification
import com.ieum.notification.repository.DeviceTokenRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component

// 앱이 꺼져 있어도 받도록 계정의 모든 기기로 보낸다. 키가 없는 로컬 / 테스트에서는 빈을 만들지 않는다.
@Component
@ConditionalOnExpression(FcmClient.ENABLED)
class FcmNotificationSender(
	private val fcmClient: FcmClient,
	private val deviceTokenRepository: DeviceTokenRepository,
) : NotificationSender {

	override fun send(notification: Notification) {
		val deviceTokens = deviceTokenRepository.findAllByAccountIdAndIsDeletedFalse(notification.accountId)
		if (deviceTokens.isEmpty()) {
			return
		}

		// 앱이 알림을 눌렀을 때 이동할 화면을 정하는 값이다.
		val data = listOfNotNull(
			"notificationId" to notification.notificationId.toString(),
			"type" to notification.type.name,
			notification.targetType?.let { "targetType" to it.name },
			notification.targetId?.let { "targetId" to it.toString() },
		).toMap()
		val invalid = fcmClient.send(deviceTokens.map { it.token }.toSet(), notification.title, notification.body, data)

		deviceTokens.filter { it.token in invalid }.forEach {
			it.delete()
			deviceTokenRepository.save(it)
		}
	}
}
