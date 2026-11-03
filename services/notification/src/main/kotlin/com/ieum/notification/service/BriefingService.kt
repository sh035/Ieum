package com.ieum.notification.service

import com.ieum.events.NotificationEventType
import com.ieum.grpc.client.grocery.GroceryClient
import com.ieum.grpc.client.planner.PlannerClient
import com.ieum.notification.domain.enums.BriefingItem
import com.ieum.notification.repository.DeviceTokenRepository
import com.ieum.web.exception.IeumException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
class BriefingService(
	private val plannerClient: PlannerClient,
	private val groceryClient: GroceryClient,
	private val deviceTokenRepository: DeviceTokenRepository,
	private val notificationService: NotificationService,
	private val composer: NotificationMessageComposer,
	private val clock: Clock,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// planner / grocery 에 오늘 개수를 물어 합치고, 챙길 게 있는 계정과 기기 토큰이 있는 계정에 한 통씩 보낸다.
	// 한 곳이라도 조회에 실패하면 챙길 일이 있는 사람에게 빈 브리핑이 갈 수 있어서 빈 브리핑은 보내지 않는다.
	fun sendBriefings() {
		val today = LocalDate.now(clock)
		val planner = fetch("planner") { plannerClient.getBriefingCounts(today) }
		val grocery = fetch("grocery") { groceryClient.getExpiringCounts(today) }

		val counts = mutableMapOf<UUID, MutableMap<BriefingItem, Int>>()
		planner?.forEach { (accountId, count) ->
			counts.getOrPut(accountId) { mutableMapOf() }.putAll(
				mapOf(BriefingItem.TODO to count.todo, BriefingItem.OVERDUE_TODO to count.overdueTodo, BriefingItem.SCHEDULE to count.schedule),
			)
		}
		grocery?.forEach { (accountId, count) -> counts.getOrPut(accountId) { mutableMapOf() }[BriefingItem.EXPIRING] = count }

		val recipients = if (planner != null && grocery != null) {
			counts.keys + deviceTokenRepository.findAllByIsDeletedFalse().map { it.accountId }
		} else {
			counts.keys
		}

		recipients.forEach { accountId ->
			// 한 계정 저장이 실패해도 나머지 계정의 브리핑은 보낸다.
			try {
				notificationService.saveAndDeliver(
					accountId = accountId,
					type = NotificationEventType.DAILY_BRIEFING,
					message = composer.briefing(counts[accountId].orEmpty()),
					dedupKey = "DAILY_BRIEFING:$today",
				)
			} catch (e: Exception) {
				log.warn("Briefing save failed. accountId={}", accountId, e)
			}
		}
	}

	// 배포 직후 첫 호출은 연결과 초기화로 deadline 을 넘기기 쉬워서 한 번 더 묻는다. 읽기 전용이라 다시 물어도 안전하다.
	private fun <T> fetch(source: String, call: () -> T): T? {
		repeat(2) { attempt ->
			try {
				return call()
			} catch (e: IeumException) {
				log.warn("Briefing count lookup failed. source={}, attempt={}", source, attempt + 1, e)
			}
		}
		return null
	}
}
