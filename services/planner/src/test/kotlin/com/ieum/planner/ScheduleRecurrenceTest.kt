package com.ieum.planner

import com.ieum.planner.domain.entity.Schedule
import com.ieum.planner.domain.enums.RepeatType
import com.ieum.planner.domain.enums.Visibility
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleRecurrenceTest {

	private fun schedule(
		start: LocalDateTime,
		end: LocalDateTime = start.plusHours(1),
		repeatType: RepeatType? = null,
		until: LocalDate? = null,
		isAllDay: Boolean = false,
	) = Schedule(
		familyId = null,
		creatorAccountId = UUID.randomUUID(),
		title = "가족 식사",
		memo = null,
		visibility = Visibility.PRIVATE,
		isAllDay = isAllDay,
		startAt = start,
		endAt = end,
		repeatType = repeatType,
		repeatUntil = until,
	)

	private fun at(date: String, time: String = "18:00") = LocalDateTime.parse("${date}T$time")

	private fun day(date: String) = LocalDate.parse(date).atStartOfDay()

	@Test
	fun `매주 반복은 첫 회차의 요일마다 기간 안의 회차를 준다`() {
		val sunday = schedule(at("2026-11-15"), repeatType = RepeatType.WEEKLY)

		assertEquals(
			listOf(at("2026-11-22"), at("2026-11-29"), at("2026-12-06")),
			sunday.occurrenceStartsIn(day("2026-11-16"), day("2026-12-07")),
		)
	}

	@Test
	fun `끝없는 반복은 먼 미래에도 보인다`() {
		val sunday = schedule(at("2026-11-15"), repeatType = RepeatType.WEEKLY)

		assertEquals(
			listOf(at("2029-07-01"), at("2029-07-08")),
			sunday.occurrenceStartsIn(day("2029-07-01"), day("2029-07-09")),
		)
	}

	@Test
	fun `매월 31일은 없는 달에 말일이 되고 다음 달에는 다시 31일이다`() {
		val monthly = schedule(at("2026-01-31", "09:00"), repeatType = RepeatType.MONTHLY)

		assertEquals(
			listOf(at("2026-01-31", "09:00"), at("2026-02-28", "09:00"), at("2026-03-31", "09:00"), at("2026-04-30", "09:00")),
			monthly.occurrenceStartsIn(day("2026-01-01"), day("2026-05-01")),
		)
	}

	@Test
	fun `매월 31일 반복을 먼 미래의 2월 기간으로 보면 그달 말일 회차를 준다`() {
		val monthly = schedule(at("2026-01-31", "09:00"), repeatType = RepeatType.MONTHLY)

		assertEquals(listOf(at("2027-02-28", "09:00")), monthly.occurrenceStartsIn(day("2027-02-01"), day("2027-03-01")))
	}

	@Test
	fun `종료일 당일 회차까지 포함한다`() {
		val daily = schedule(at("2026-11-12", "07:00"), repeatType = RepeatType.DAILY, until = LocalDate.parse("2026-11-14"))

		assertEquals(
			listOf(at("2026-11-12", "07:00"), at("2026-11-13", "07:00"), at("2026-11-14", "07:00")),
			daily.occurrenceStartsIn(day("2026-11-01"), day("2026-12-01")),
		)
	}

	@Test
	fun `첫 회차 전 기간에는 회차가 없다`() {
		val daily = schedule(at("2026-11-12"), repeatType = RepeatType.DAILY)

		assertEquals(emptyList(), daily.occurrenceStartsIn(day("2026-11-01"), day("2026-11-12")))
	}

	@Test
	fun `기간 앞에서 시작해 기간에 걸친 여러 날 회차도 준다`() {
		// 금요일 저녁부터 일요일 저녁까지 매주 캠핑
		val camping = schedule(at("2026-11-13"), end = at("2026-11-15"), repeatType = RepeatType.WEEKLY)

		assertEquals(
			listOf(at("2026-11-20"), at("2026-11-27")),
			camping.occurrenceStartsIn(day("2026-11-21"), day("2026-11-28")),
		)
	}

	@Test
	fun `자정에 끝나는 시각 회차는 다음 날 기간에 넣지 않고 종일 회차는 끝 날짜를 포함한다`() {
		val untilMidnight = schedule(at("2026-11-12", "22:00"), end = day("2026-11-13"), repeatType = RepeatType.DAILY)
		val allDay = schedule(day("2026-11-12"), end = day("2026-11-12"), repeatType = RepeatType.DAILY, isAllDay = true)

		assertEquals(listOf(at("2026-11-13", "22:00")), untilMidnight.occurrenceStartsIn(day("2026-11-13"), day("2026-11-14")))
		assertEquals(listOf(day("2026-11-13")), allDay.occurrenceStartsIn(day("2026-11-13"), day("2026-11-14")))
	}

	@Test
	fun `반복이 아니면 겹칠 때 자기 자신 하나다`() {
		val once = schedule(at("2026-11-12"))

		assertEquals(listOf(at("2026-11-12")), once.occurrenceStartsIn(day("2026-11-12"), day("2026-11-13")))
		assertEquals(emptyList(), once.occurrenceStartsIn(day("2026-11-13"), day("2026-11-14")))
	}

	@Test
	fun `회차인지 확인한다`() {
		val monthly = schedule(at("2026-01-31", "09:00"), repeatType = RepeatType.MONTHLY, until = LocalDate.parse("2026-03-31"))

		assertTrue(monthly.isOccurrence(at("2026-02-28", "09:00")))
		assertTrue(monthly.isOccurrence(at("2026-03-31", "09:00")))
		assertFalse(monthly.isOccurrence(at("2026-02-28", "10:00")))
		assertFalse(monthly.isOccurrence(at("2026-04-30", "09:00")))
		assertFalse(monthly.isOccurrence(at("2025-12-31", "09:00")))
	}
}
