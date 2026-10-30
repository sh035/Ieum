package com.ieum.notification.service

import com.ieum.events.NotificationEventData
import com.ieum.events.NotificationEventType
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// 문구는 여기 한 곳에서 만든다. 본문은 잠금 화면 한 줄에 들어가게 짧게 쓴다.
@Component
class NotificationMessageComposer {

	fun compose(type: NotificationEventType, data: NotificationEventData, actorNickname: String?, familyName: String?): NotificationMessage {
		val nickname = actorNickname ?: UNKNOWN
		// 제목 / 금액이 빠진 이벤트는 틀린 문구로 저장하지 않고 IllegalArgumentException 으로 DLT 에 보낸다.
		val subject by lazy { requireNotNull(data.subject) { "subject is required. type=$type" }.shorten() }
		val amount by lazy { won(requireNotNull(data.amount) { "amount is required. type=$type" }) }

		return when (type) {
			NotificationEventType.TODO_ASSIGNED ->
				NotificationMessage("${nickname}님이 할 일을 맡겼어요", listOfNotNull(subject, data.at?.let(::dateTime)).joinToString(" · "))
			NotificationEventType.TODO_COMPLETED -> NotificationMessage("${nickname}님이 할 일을 마쳤어요", subject)
			NotificationEventType.SCHEDULE_ATTENDEE_ADDED -> NotificationMessage("${nickname}님이 일정에 초대했어요", dated(data.at, subject))
			NotificationEventType.FAMILY_SCHEDULE_CREATED -> NotificationMessage("[${familyName ?: UNKNOWN}] 가족 일정", dated(data.at, subject))
			NotificationEventType.SCHEDULE_UPDATED -> NotificationMessage("일정이 바뀌었어요", dated(data.at, subject))
			NotificationEventType.SCHEDULE_CANCELED -> NotificationMessage("일정이 취소됐어요", subject)
			NotificationEventType.FAMILY_MEMBER_JOINED -> NotificationMessage("새 가족이 들어왔어요", "${nickname}님이 함께해요")
			NotificationEventType.FAMILY_ROLE_CHANGED -> NotificationMessage(
				if (data.role == MANAGER) "살림을 함께 꾸리게 됐어요" else "살림 함께 꾸리기가 꺼졌어요",
				"${nickname}님이 바꿨어요",
			)
			NotificationEventType.EXPENSE_PAYER_ASSIGNED ->
				NotificationMessage("${nickname}님이 지출을 등록했어요", "$subject · $amount")
			// 지난달 영수증을 늦게 넣을 수 있어서 판정한 달을 쓴다.
			NotificationEventType.BUDGET_EXCEEDED ->
				NotificationMessage("${data.at?.let { "${it.monthValue}월" } ?: "이번 달"} 예산을 넘었어요", "$amount 초과")
			NotificationEventType.TODO_DUE_SOON -> NotificationMessage("3시간 뒤 마감이에요", subject)
			NotificationEventType.SCHEDULE_STARTING_SOON -> NotificationMessage("3시간 뒤 일정이 있어요", subject)
		}
	}

	private fun dated(at: LocalDateTime?, subject: String) = listOfNotNull(at?.format(DATE), subject).joinToString(" ")

	// 할 일 / 일정 제목은 255자까지라 본문 컬럼(200자)과 잠금 화면 한 줄에 맞게 자른다.
	private fun String.shorten() = if (length > MAX_SUBJECT_LENGTH) take(MAX_SUBJECT_LENGTH) + "…" else this

	// 날짜만 있는 마감은 자정으로 오므로 시각을 빼고 쓴다.
	private fun dateTime(at: LocalDateTime) =
		if (at.toLocalTime() == LocalTime.MIDNIGHT) at.format(DATE) else "${at.format(DATE)} ${at.format(TIME)}"

	private fun won(amount: Int) = "%,d원".format(Locale.KOREA, amount)

	companion object {
		private const val UNKNOWN = "가족"
		private const val MANAGER = "MANAGER"
		private const val MAX_SUBJECT_LENGTH = 50
		private val DATE = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN)
		private val TIME = DateTimeFormatter.ofPattern("HH:mm")
	}
}
