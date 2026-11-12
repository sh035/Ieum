package com.ieum.planner

import com.ieum.grpc.client.family.FamilyClient
import com.ieum.planner.repository.ScheduleAttendeeRepository
import com.ieum.planner.repository.ScheduleRepository
import com.ieum.planner.service.BriefingCountService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
class RecurringScheduleTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
	private val briefingCountService: BriefingCountService,
	private val objectMapper: ObjectMapper,
) : PostgresTestSupport() {

	// 개인 일정만 써서 family 를 부르지 않는다.
	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val me = UUID.randomUUID()

	// 회차를 대신하는 행이 원본을 참조해서 한 문장으로 지운다. 다른 테스트의 deleteAll 이 걸리지 않게 끝날 때도 비운다.
	@BeforeEach
	@AfterEach
	fun clean() {
		scheduleAttendeeRepository.deleteAllInBatch()
		scheduleRepository.deleteAllInBatch()
	}

	private fun body(
		title: String = "가족 식사",
		startAt: String = "2026-11-15T18:00:00",
		endAt: String = "2026-11-15T20:00:00",
		repeat: String? = "WEEKLY",
		until: String? = null,
		withVisibility: Boolean = true,
	) = buildString {
		append("{")
		if (withVisibility) append(""""visibility":"PRIVATE",""")
		append(""""title":"$title","isAllDay":false,"startAt":"$startAt","endAt":"$endAt"""")
		repeat?.let { append(""","repeatType":"$it"""") }
		until?.let { append(""","repeatUntil":"$it"""") }
		append("}")
	}

	private fun data(actions: ResultActionsDsl): JsonNode =
		objectMapper.readTree(actions.andReturn().response.contentAsString).get("data")

	private fun create(body: String = body()) = mockMvc.post("/api/v1/schedule") {
		header("Authorization", "Bearer ${accessToken(me)}")
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private fun createId(body: String = body()): String = data(create(body).andExpect { status { isCreated() } }).get("scheduleId").asString()

	private fun list(from: String, to: String) = data(
		mockMvc.get("/api/v1/schedule") {
			header("Authorization", "Bearer ${accessToken(me)}")
			param("from", from)
			param("to", to)
		}.andExpect { status { isOk() } },
	)

	// 회차 시작 → 제목
	private fun titles(from: String, to: String): Map<String, String> =
		list(from, to).associate { it.get("startAt").asString() to it.get("title").asString() }

	private fun update(id: String, body: String, occurrence: String? = null, scope: String? = null) = mockMvc.patch("/api/v1/schedule/$id") {
		header("Authorization", "Bearer ${accessToken(me)}")
		occurrence?.let { param("occurrenceStartAt", it) }
		scope?.let { param("scope", it) }
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private fun delete(id: String, occurrence: String? = null, scope: String? = null) = mockMvc.delete("/api/v1/schedule/$id") {
		header("Authorization", "Bearer ${accessToken(me)}")
		occurrence?.let { param("occurrenceStartAt", it) }
		scope?.let { param("scope", it) }
	}

	@Test
	fun `반복 일정을 만들면 첫 회차와 반복 규칙을 돌려주고 먼 미래에도 회차가 보인다`() {
		create().andExpect {
			status { isCreated() }
			jsonPath("\$.data.repeatType") { value("WEEKLY") }
			jsonPath("\$.data.occurrenceStartAt") { value("2026-11-15T18:00:00") }
		}

		assertEquals(
			mapOf("2026-11-15T18:00:00" to "가족 식사", "2026-11-22T18:00:00" to "가족 식사"),
			titles("2026-11-14", "2026-11-28"),
		)
		assertEquals(setOf("2029-07-01T18:00:00", "2029-07-08T18:00:00"), titles("2029-07-01", "2029-07-08").keys)
	}

	@Test
	fun `종료일이 반복 없이 오거나 첫 회차보다 앞서면 400`() {
		create(body(repeat = null, until = "2026-12-31")).andExpect { status { isBadRequest() } }
		create(body(until = "2026-11-14")).andExpect { status { isBadRequest() } }
	}

	@Test
	fun `이 일정만 고치면 그 회차만 바뀌고 원본을 가리킨다`() {
		val id = createId()

		val replaced = data(
			update(id, body("늦은 식사", "2026-11-22T19:00:00", "2026-11-22T21:00:00", repeat = null, withVisibility = false), "2026-11-22T18:00:00", "THIS")
				.andExpect { status { isOk() } },
		)

		assertEquals(id, replaced.get("recurringScheduleId").asString())
		assertEquals("2026-11-22T18:00:00", replaced.get("occurrenceStartAt").asString())
		assertEquals(
			mapOf("2026-11-15T18:00:00" to "가족 식사", "2026-11-22T19:00:00" to "늦은 식사", "2026-11-29T18:00:00" to "가족 식사"),
			titles("2026-11-15", "2026-11-29"),
		)
	}

	@Test
	fun `이 일정만 지우면 그 회차만 빠지고 상세 조회는 404`() {
		val id = createId()

		delete(id, "2026-11-22T18:00:00", "THIS").andExpect { status { isNoContent() } }

		assertEquals(setOf("2026-11-15T18:00:00", "2026-11-29T18:00:00"), titles("2026-11-15", "2026-11-29").keys)
		mockMvc.get("/api/v1/schedule/$id") {
			header("Authorization", "Bearer ${accessToken(me)}")
			param("occurrenceStartAt", "2026-11-22T18:00:00")
		}.andExpect { status { isNotFound() } }
		delete(id, "2026-11-22T18:00:00", "THIS").andExpect { status { isNotFound() } }
	}

	@Test
	fun `이후 모두 고치면 그 회차부터 새 반복이 되고 앞 회차는 그대로다`() {
		val id = createId()
		// 이 일정만 고칠 때는 반복을 바꿀 수 없다.
		update(id, body("저녁 산책", "2026-12-05T18:00:00", "2026-12-05T20:00:00", repeat = "WEEKLY", withVisibility = false), "2026-11-15T18:00:00", "THIS")
			.andExpect { status { isBadRequest() } }

		val following = data(
			update(id, body("주말 외식", "2026-11-29T12:00:00", "2026-11-29T14:00:00", withVisibility = false), "2026-11-29T18:00:00", "FOLLOWING")
				.andExpect { status { isOk() } },
		)

		assertEquals(LocalDate.parse("2026-11-28"), scheduleRepository.findById(UUID.fromString(id)).get().repeatUntil)
		assertEquals("WEEKLY", following.get("repeatType").asString())
		assertEquals(
			mapOf(
				"2026-11-22T18:00:00" to "가족 식사",
				"2026-11-29T12:00:00" to "주말 외식",
				"2026-12-06T12:00:00" to "주말 외식",
			),
			titles("2026-11-16", "2026-12-06"),
		)
	}

	@Test
	fun `첫 회차부터 제목만 고치면 원본을 고치고 따로 바꾼 회차는 남는다`() {
		val id = createId()
		update(id, body("늦은 식사", "2026-11-22T19:00:00", "2026-11-22T21:00:00", repeat = null, withVisibility = false), "2026-11-22T18:00:00", "THIS")

		update(id, body("저녁 식사", withVisibility = false), "2026-11-15T18:00:00", "FOLLOWING").andExpect {
			status { isOk() }
			jsonPath("\$.data.scheduleId") { value(id) }
		}

		assertEquals(
			mapOf("2026-11-15T18:00:00" to "저녁 식사", "2026-11-22T19:00:00" to "늦은 식사", "2026-11-29T18:00:00" to "저녁 식사"),
			titles("2026-11-15", "2026-11-29"),
		)
	}

	@Test
	fun `첫 회차를 따로 바꾼 뒤 그 회차에서 이후 모두를 고치면 그 회차도 같은 내용이 된다`() {
		val id = createId()
		val replacedId = data(
			update(id, body("늦은 식사", repeat = null, withVisibility = false), "2026-11-15T18:00:00", "THIS"),
		).get("scheduleId").asString()

		update(replacedId, body("저녁 식사", withVisibility = false), scope = "FOLLOWING").andExpect { status { isOk() } }

		assertEquals(
			mapOf("2026-11-15T18:00:00" to "저녁 식사", "2026-11-22T18:00:00" to "저녁 식사"),
			titles("2026-11-15", "2026-11-22"),
		)
	}

	@Test
	fun `첫 회차부터 시작이나 반복을 바꾸면 새 반복으로 바꾸고 따로 바꾼 회차는 사라진다`() {
		val id = createId()
		update(id, body("늦은 식사", "2026-11-22T19:00:00", "2026-11-22T21:00:00", repeat = null, withVisibility = false), "2026-11-22T18:00:00", "THIS")

		val replaced = data(
			update(id, body("아침 식사", "2026-11-15T08:00:00", "2026-11-15T09:00:00", repeat = "DAILY", until = "2026-11-17", withVisibility = false), "2026-11-15T18:00:00", "FOLLOWING")
				.andExpect { status { isOk() } },
		)

		assertEquals(true, replaced.get("scheduleId").asString() != id)
		assertEquals(
			mapOf("2026-11-15T08:00:00" to "아침 식사", "2026-11-16T08:00:00" to "아침 식사", "2026-11-17T08:00:00" to "아침 식사"),
			titles("2026-11-14", "2026-11-30"),
		)
	}

	@Test
	fun `회차 상세는 그 회차의 시작과 끝을 돌려준다`() {
		val id = createId()

		mockMvc.get("/api/v1/schedule/$id") {
			header("Authorization", "Bearer ${accessToken(me)}")
			param("occurrenceStartAt", "2026-12-06T18:00:00")
		}.andExpect {
			status { isOk() }
			jsonPath("\$.data.scheduleId") { value(id) }
			jsonPath("\$.data.startAt") { value("2026-12-06T18:00:00") }
			jsonPath("\$.data.endAt") { value("2026-12-06T20:00:00") }
			jsonPath("\$.data.occurrenceStartAt") { value("2026-12-06T18:00:00") }
		}
	}

	@Test
	fun `일정 목록은 42일보다 긴 기간이면 400`() {
		createId()

		mockMvc.get("/api/v1/schedule") {
			header("Authorization", "Bearer ${accessToken(me)}")
			param("from", "2026-01-01")
			param("to", "9999-12-31")
		}.andExpect { status { isBadRequest() } }
	}

	@Test
	fun `오늘 브리핑에 오늘 회차가 나온다`() {
		val today = LocalDate.now()
		createId(body(startAt = "${today.minusDays(3)}T07:00:00", endAt = "${today.minusDays(3)}T08:00:00", repeat = "DAILY"))

		mockMvc.get("/api/v1/daily-briefing") { header("Authorization", "Bearer ${accessToken(me)}") }.andExpect {
			status { isOk() }
			jsonPath("\$.data.schedules.length()") { value(1) }
			jsonPath("\$.data.schedules[0].startAt") { value("${today}T07:00:00") }
		}
	}

	@Test
	fun `종료일을 앞당기면 그 뒤에 따로 바꾼 회차도 사라진다`() {
		val id = createId()
		update(id, body("늦은 식사", "2026-11-29T19:00:00", "2026-11-29T21:00:00", repeat = null, withVisibility = false), "2026-11-29T18:00:00", "THIS")

		update(id, body(until = "2026-11-22", withVisibility = false), "2026-11-15T18:00:00", "FOLLOWING").andExpect { status { isOk() } }

		assertEquals(setOf("2026-11-15T18:00:00", "2026-11-22T18:00:00"), titles("2026-11-15", "2026-12-20").keys)
	}

	@Test
	fun `따로 바꾼 회차에서 이후 모두를 고르면 원래 회차부터 적용한다`() {
		val id = createId()
		val replacedId = data(
			update(id, body("늦은 식사", "2026-11-22T19:00:00", "2026-11-22T21:00:00", repeat = null, withVisibility = false), "2026-11-22T18:00:00", "THIS"),
		).get("scheduleId").asString()

		delete(replacedId, scope = "FOLLOWING").andExpect { status { isNoContent() } }

		assertEquals(setOf("2026-11-15T18:00:00"), titles("2026-11-15", "2026-12-20").keys)
	}

	@Test
	fun `이후 모두를 첫 회차에서 지우면 반복 전체가 사라진다`() {
		val id = createId()

		delete(id, "2026-11-15T18:00:00", "FOLLOWING").andExpect { status { isNoContent() } }

		assertEquals(emptyMap(), titles("2026-11-01", "2026-12-12"))
	}

	@Test
	fun `반복 일정은 회차와 범위가 필요하고 회차가 아닌 시각은 404, 일반 일정에 회차를 주면 400`() {
		val id = createId()
		val once = createId(body(repeat = null))

		update(id, body(withVisibility = false)).andExpect { status { isBadRequest() } }
		delete(id).andExpect { status { isBadRequest() } }
		delete(id, "2026-11-16T18:00:00", "THIS").andExpect { status { isNotFound() } }
		delete(once, "2026-11-15T18:00:00", "THIS").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `일반 일정에 반복을 주면 반복 일정이 된다`() {
		val id = createId(body(repeat = null))

		update(id, body(repeat = "DAILY", until = "2026-11-17", withVisibility = false)).andExpect { status { isOk() } }

		assertEquals(3, titles("2026-11-01", "2026-11-30").size)
	}

	@Test
	fun `브리핑 개수는 그날 회차를 세고 취소한 회차는 세지 않는다`() {
		val id = createId()
		delete(id, "2026-11-22T18:00:00", "THIS")

		assertEquals(1, briefingCountService.countsOn(LocalDate.parse("2026-11-29"))[me]?.schedule)
		assertNull(briefingCountService.countsOn(LocalDate.parse("2026-11-22"))[me])
	}
}
