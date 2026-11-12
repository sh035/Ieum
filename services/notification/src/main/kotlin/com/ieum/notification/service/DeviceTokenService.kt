package com.ieum.notification.service

import com.ieum.notification.controller.dto.request.RegisterDeviceTokenRequest
import com.ieum.notification.domain.entity.DeviceToken
import com.ieum.notification.repository.DeviceTokenRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DeviceTokenService(
	private val deviceTokenRepository: DeviceTokenRepository,
) {

	// 토큰은 기기 하나에 하나라서, 다른 계정이 같은 토큰을 등록하면 기기 주인이 바뀐 것으로 보고 옮긴다.
	fun register(accountId: String, request: RegisterDeviceTokenRequest) {
		val me = UUID.fromString(accountId)
		val deviceToken = deviceTokenRepository.findByTokenAndIsDeletedFalse(request.token)
			?.apply {
				this.accountId = me
				platform = request.platform
			}
			?: DeviceToken(accountId = me, token = request.token, platform = request.platform)

		// 같은 토큰을 동시에 처음 등록하면 unique index 에 걸린다. 한쪽이 이미 등록했으므로 그대로 둔다.
		try {
			deviceTokenRepository.saveAndFlush(deviceToken)
		} catch (e: DataIntegrityViolationException) {
			return
		}
	}

	// 탈퇴한 계정의 기기로 푸시가 가지 않게 지운다.
	@Transactional
	fun deleteAll(accountId: UUID) {
		deviceTokenRepository.findAllByAccountIdAndIsDeletedFalse(accountId).forEach { it.delete() }
	}

	// 로그아웃마다 부르므로 없는 토큰이나 남의 토큰이어도 성공으로 끝낸다.
	fun unregister(accountId: String, token: String) {
		val deviceToken = deviceTokenRepository.findByTokenAndIsDeletedFalse(token)
			?.takeIf { it.accountId == UUID.fromString(accountId) }
			?: return

		deviceToken.delete()
		deviceTokenRepository.save(deviceToken)
	}
}
