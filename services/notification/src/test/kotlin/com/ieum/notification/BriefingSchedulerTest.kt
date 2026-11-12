package com.ieum.notification

import com.ieum.events.NotificationEventType
import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.grpc.client.account.AccountClient
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.grpc.client.grocery.GroceryClient
import com.ieum.grpc.client.planner.PlannerBriefingCounts
import com.ieum.grpc.client.planner.PlannerClient
import com.ieum.notification.domain.entity.DeviceToken
import com.ieum.notification.domain.enums.DevicePlatform
import com.ieum.notification.repository.DeviceTokenRepository
import com.ieum.notification.repository.NotificationRepository
import com.ieum.notification.scheduler.BriefingScheduler
import com.ieum.notification.service.NotificationMessage
import com.ieum.notification.service.NotificationService
import com.ieum.scheduler.ShedlockRepository
import com.ieum.web.config.TimeZoneConfig
import com.ieum.web.exception.IeumException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@Import(BriefingSchedulerTest.TestClock::class)
class BriefingSchedulerTest @Autowired constructor(
	private val briefingScheduler: BriefingScheduler,
	private val deviceTokenRepository: DeviceTokenRepository,
	private val notificationRepository: NotificationRepository,
	private val shedlockRepository: ShedlockRepository,
) : PostgresKafkaTestSupport() {

	@TestConfiguration
	class TestClock {
		@Bean
		@Primary
		fun fixedClock(): Clock = Clock.fixed(LocalDateTime.of(2026, 10, 30, 9, 0).atZone(TimeZoneConfig.SEOUL).toInstant(), TimeZoneConfig.SEOUL)
	}

	@MockitoBean
	private lateinit var accountClient: AccountClient

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	@MockitoBean
	private lateinit var plannerClient: PlannerClient

	@MockitoBean
	private lateinit var groceryClient: GroceryClient

	@MockitoSpyBean
	private lateinit var notificationService: NotificationService

	private val mom = UUID.randomUUID()
	private val dad = UUID.randomUUID()
	private val kid = UUID.randomUUID()
	private val today = LocalDate.of(2026, 10, 30)
	private val unavailable = IeumException(GrpcClientErrorCode.PLANNER_SERVICE_UNAVAILABLE)

	@BeforeEach
	fun setUp() {
		notificationRepository.deleteAllInBatch()
		deviceTokenRepository.deleteAllInBatch()
		releaseLock()
		// 기기 토큰만 있는 계정은 account 에 있는지 확인한 뒤 빈 브리핑을 받는다.
		`when`(accountClient.getNicknames(setOf(dad))).thenReturn(mapOf(dad to "아빠"))
	}

	private fun releaseLock() {
		shedlockRepository.saveAll(shedlockRepository.findAll().onEach { it.lockUntil = LocalDateTime.of(2000, 1, 1, 0, 0) })
	}

	private fun token(accountId: UUID, deleted: Boolean = false) =
		deviceTokenRepository.save(DeviceToken(accountId, "token-$accountId", DevicePlatform.ANDROID).apply { if (deleted) delete() })

	private fun briefings() = notificationRepository.findAll()
		.filter { it.type == NotificationEventType.DAILY_BRIEFING }
		.associate { it.accountId to Triple(it.title, it.body, it.dedupKey) }

	private fun briefing(body: String) = Triple("오늘의 데일리 브리핑", body, "DAILY_BRIEFING:2026-10-30")

	@Test
	fun `planner 와 grocery 의 오늘 개수를 계정별로 합쳐 한 통씩 저장한다`() {
		`when`(plannerClient.getBriefingCounts(today)).thenReturn(
			mapOf(
				mom to PlannerBriefingCounts(todo = 2, overdueTodo = 0, schedule = 1),
				kid to PlannerBriefingCounts(todo = 0, overdueTodo = 1, schedule = 0),
			),
		)
		`when`(groceryClient.getExpiringCounts(today)).thenReturn(mapOf(mom to 3, dad to 1))

		briefingScheduler.run()

		assertEquals(
			mapOf(
				mom to briefing("할 일 2 · 일정 1 · 유통기한 3"),
				kid to briefing("밀린 할 일 1"),
				dad to briefing("유통기한 1"),
			),
			briefings(),
		)
		assertEquals(listOf(null), notificationRepository.findAll().map { it.familyId }.distinct())
	}

	@Test
	fun `챙길 게 없어도 기기 토큰이 있는 계정에는 빈 브리핑을 보낸다`() {
		token(dad)
		token(kid, deleted = true)
		`when`(groceryClient.getExpiringCounts(today)).thenReturn(mapOf(mom to 1))

		briefingScheduler.run()

		assertEquals(
			mapOf(mom to briefing("유통기한 1"), dad to briefing("오늘은 챙길 일이 없어요. 여유로운 하루 보내세요")),
			briefings(),
		)
	}

	@Test
	fun `탈퇴해서 account 에 없는 계정의 기기 토큰에는 빈 브리핑을 보내지 않는다`() {
		val withdrawn = UUID.randomUUID()
		token(dad)
		token(withdrawn)
		`when`(accountClient.getNicknames(setOf(dad, withdrawn))).thenReturn(mapOf(dad to "아빠"))

		briefingScheduler.run()

		assertEquals(setOf(dad), briefings().keys)
	}

	@Test
	fun `한 곳이라도 조회에 실패하면 빈 브리핑은 보내지 않고 받은 개수로만 보낸다`() {
		token(dad)
		`when`(plannerClient.getBriefingCounts(today)).thenThrow(unavailable)
		`when`(groceryClient.getExpiringCounts(today)).thenReturn(mapOf(mom to 2))

		briefingScheduler.run()

		assertEquals(mapOf(mom to briefing("유통기한 2")), briefings())
	}

	@Test
	fun `두 곳 모두 조회에 실패하면 아무에게도 보내지 않는다`() {
		token(dad)
		`when`(plannerClient.getBriefingCounts(today)).thenThrow(unavailable)
		`when`(groceryClient.getExpiringCounts(today)).thenThrow(IeumException(GrpcClientErrorCode.GROCERY_SERVICE_UNAVAILABLE))

		briefingScheduler.run()

		assertEquals(emptyMap(), briefings())
	}

	@Test
	fun `조회에 실패한 뒤 다시 돌면 브리핑을 보낸다`() {
		`when`(plannerClient.getBriefingCounts(today))
			.thenThrow(unavailable)
			.thenThrow(unavailable)
			.thenReturn(mapOf(mom to PlannerBriefingCounts(todo = 1, overdueTodo = 0, schedule = 0)))
		`when`(groceryClient.getExpiringCounts(today))
			.thenThrow(IeumException(GrpcClientErrorCode.GROCERY_SERVICE_UNAVAILABLE))
			.thenThrow(IeumException(GrpcClientErrorCode.GROCERY_SERVICE_UNAVAILABLE))
			.thenReturn(emptyMap())

		briefingScheduler.run()
		assertEquals(emptyMap(), briefings())

		releaseLock()
		briefingScheduler.run()
		assertEquals(mapOf(mom to briefing("할 일 1")), briefings())
	}

	@Test
	fun `grocery 조회에 실패해도 빈 브리핑은 보내지 않고 planner 개수로만 보낸다`() {
		token(dad)
		`when`(plannerClient.getBriefingCounts(today)).thenReturn(mapOf(mom to PlannerBriefingCounts(todo = 1, overdueTodo = 0, schedule = 0)))
		`when`(groceryClient.getExpiringCounts(today)).thenThrow(IeumException(GrpcClientErrorCode.GROCERY_SERVICE_UNAVAILABLE))

		briefingScheduler.run()

		assertEquals(mapOf(mom to briefing("할 일 1")), briefings())
	}

	@Test
	fun `조회가 한 번 실패해도 다시 물어 받은 개수로 보낸다`() {
		token(dad)
		`when`(groceryClient.getExpiringCounts(today)).thenThrow(unavailable).thenReturn(mapOf(mom to 2))

		briefingScheduler.run()

		assertEquals(mapOf(mom to briefing("유통기한 2"), dad to briefing("오늘은 챙길 일이 없어요. 여유로운 하루 보내세요")), briefings())
	}

	@Test
	fun `한 계정 저장이 실패해도 나머지 계정은 받는다`() {
		`when`(groceryClient.getExpiringCounts(today)).thenReturn(mapOf(mom to 1, dad to 2))
		doThrow(IllegalStateException("boom")).`when`(notificationService).saveAndDeliver(
			mom,
			NotificationEventType.DAILY_BRIEFING,
			NotificationMessage("오늘의 데일리 브리핑", "유통기한 1"),
			"DAILY_BRIEFING:2026-10-30",
		)

		briefingScheduler.run()

		assertEquals(mapOf(dad to briefing("유통기한 2")), briefings())
	}

	@Test
	fun `같은 날 두 번 돌아도 한 통이다`() {
		`when`(groceryClient.getExpiringCounts(today)).thenReturn(mapOf(mom to 1))

		briefingScheduler.run()
		releaseLock()
		briefingScheduler.run()

		assertEquals(1, notificationRepository.count())
	}

	@Test
	fun `여러 Pod 가 함께 돌지 않도록 shedlock 테이블에 락을 남긴다`() {
		briefingScheduler.run()

		assertTrue(shedlockRepository.existsById("notification-briefing"))
	}
}
