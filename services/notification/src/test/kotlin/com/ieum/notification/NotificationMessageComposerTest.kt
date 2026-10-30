package com.ieum.notification

import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationEventType.BUDGET_EXCEEDED
import com.ieum.events.NotificationEventType.EXPENSE_PAYER_ASSIGNED
import com.ieum.events.NotificationEventType.FAMILY_MEMBER_JOINED
import com.ieum.events.NotificationEventType.FAMILY_ROLE_CHANGED
import com.ieum.events.NotificationEventType.FAMILY_SCHEDULE_CREATED
import com.ieum.events.NotificationEventType.SCHEDULE_ATTENDEE_ADDED
import com.ieum.events.NotificationEventType.SCHEDULE_CANCELED
import com.ieum.events.NotificationEventType.SCHEDULE_STARTING_SOON
import com.ieum.events.NotificationEventType.SCHEDULE_UPDATED
import com.ieum.events.NotificationEventType.TODO_ASSIGNED
import com.ieum.events.NotificationEventType.TODO_COMPLETED
import com.ieum.events.NotificationEventType.TODO_DUE_SOON
import com.ieum.notification.service.NotificationMessage
import com.ieum.notification.service.NotificationMessageComposer
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

class NotificationMessageComposerTest {

	private val composer = NotificationMessageComposer()

	private fun data(subject: String? = null, at: LocalDateTime? = null, amount: Int? = null, role: String? = null) =
		NotificationEventData(
			recipientAccountIds = listOf(UUID.randomUUID()),
			familyId = null,
			targetType = null,
			targetId = null,
			actorAccountId = null,
			subject = subject,
			at = at,
			amount = amount,
			role = role,
			dedupKey = null,
		)

	private fun compose(type: NotificationEventType, data: NotificationEventData, nickname: String? = "엄마", familyName: String? = null) =
		composer.compose(type, data, nickname, familyName)

	private val evening = LocalDateTime.of(2026, 10, 30, 19, 0)
	private val dateOnly = LocalDateTime.of(2026, 10, 30, 0, 0)

	@Test
	fun `할 일을 맡기면 닉네임과 마감을 쓴다`() {
		assertEquals(
			NotificationMessage("엄마님이 할 일을 맡겼어요", "분리수거하기 · 10/30(금) 19:00"),
			compose(TODO_ASSIGNED, data("분리수거하기", evening)),
		)
		assertEquals(
			NotificationMessage("엄마님이 할 일을 맡겼어요", "분리수거하기 · 10/30(금)"),
			compose(TODO_ASSIGNED, data("분리수거하기", dateOnly)),
		)
		assertEquals(NotificationMessage("엄마님이 할 일을 맡겼어요", "분리수거하기"), compose(TODO_ASSIGNED, data("분리수거하기")))
	}

	@Test
	fun `할 일과 일정 알림 문구`() {
		assertEquals(NotificationMessage("엄마님이 할 일을 마쳤어요", "분리수거하기"), compose(TODO_COMPLETED, data("분리수거하기")))
		assertEquals(
			NotificationMessage("엄마님이 일정에 초대했어요", "10/30(금) 할머니 생신"),
			compose(SCHEDULE_ATTENDEE_ADDED, data("할머니 생신", evening)),
		)
		assertEquals(
			NotificationMessage("[우리집] 가족 일정", "10/30(금) 할머니 생신"),
			compose(FAMILY_SCHEDULE_CREATED, data("할머니 생신", evening), familyName = "우리집"),
		)
		assertEquals(NotificationMessage("일정이 바뀌었어요", "10/30(금) 할머니 생신"), compose(SCHEDULE_UPDATED, data("할머니 생신", evening)))
		assertEquals(NotificationMessage("일정이 취소됐어요", "할머니 생신"), compose(SCHEDULE_CANCELED, data("할머니 생신", evening)))
		assertEquals(NotificationMessage("3시간 뒤 마감이에요", "분리수거하기"), compose(TODO_DUE_SOON, data("분리수거하기", evening)))
		assertEquals(NotificationMessage("3시간 뒤 일정이 있어요", "할머니 생신"), compose(SCHEDULE_STARTING_SOON, data("할머니 생신", evening)))
	}

	@Test
	fun `가족과 지출 알림 문구`() {
		assertEquals(NotificationMessage("새 가족이 들어왔어요", "엄마님이 함께해요"), compose(FAMILY_MEMBER_JOINED, data()))
		assertEquals(NotificationMessage("살림을 함께 꾸리게 됐어요", "엄마님이 바꿨어요"), compose(FAMILY_ROLE_CHANGED, data(role = "MANAGER")))
		assertEquals(NotificationMessage("살림 함께 꾸리기가 꺼졌어요", "엄마님이 바꿨어요"), compose(FAMILY_ROLE_CHANGED, data(role = "MEMBER")))
		assertEquals(
			NotificationMessage("엄마님이 지출을 등록했어요", "장보기 · 12,000원"),
			compose(EXPENSE_PAYER_ASSIGNED, data("장보기", amount = 12000)),
		)
		assertEquals(NotificationMessage("이번 달 예산을 넘었어요", "30,000원 초과"), compose(BUDGET_EXCEEDED, data(amount = 30000)))
	}

	@Test
	fun `닉네임이나 가족 이름을 못 찾으면 가족으로 쓴다`() {
		assertEquals(NotificationMessage("가족님이 할 일을 마쳤어요", "분리수거하기"), compose(TODO_COMPLETED, data("분리수거하기"), nickname = null))
		assertEquals(
			NotificationMessage("[가족] 가족 일정", "10/30(금) 할머니 생신"),
			compose(FAMILY_SCHEDULE_CREATED, data("할머니 생신", evening), familyName = null),
		)
	}

	@Test
	fun `긴 제목은 50자로 자르고 말줄임표를 붙인다`() {
		val long = "가".repeat(51)

		assertEquals(NotificationMessage("엄마님이 할 일을 마쳤어요", "가".repeat(50) + "…"), compose(TODO_COMPLETED, data(long)))
		assertEquals(NotificationMessage("엄마님이 할 일을 마쳤어요", "가".repeat(50)), compose(TODO_COMPLETED, data("가".repeat(50))))
	}
}
