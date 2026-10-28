package com.ieum.planner

import com.ieum.planner.repository.ScheduleAttendeeRepository
import com.ieum.planner.repository.ScheduleRepository
import com.ieum.planner.service.FamilyClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.hamcrest.Matchers.containsInAnyOrder
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val scheduleRepository: ScheduleRepository,
	private val scheduleAttendeeRepository: ScheduleAttendeeRepository,
	private val objectMapper: ObjectMapper,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val familyId = UUID.randomUUID()
	private val me = UUID.randomUUID()
	private val member = UUID.randomUUID()
	private val member2 = UUID.randomUUID()
	private val outsider = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		scheduleAttendeeRepository.deleteAll()
		scheduleRepository.deleteAll()
		listOf(me, member, member2).forEach { `when`(familyClient.isMember(familyId, it)).thenReturn(true) }
	}

	private fun create(body: String, accountId: UUID = me) = mockMvc.post("/api/v1/schedule") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private fun createId(body: String, accountId: UUID = me): String {
		val response = create(body, accountId).andReturn().response.contentAsString
		return objectMapper.readTree(response).get("data").get("scheduleId").asString()
	}

	private fun familySchedule(
		title: String = "외식",
		startAt: String = "2026-11-10T18:00:00",
		endAt: String = "2026-11-10T20:00:00",
		attendees: List<UUID> = listOf(me, member),
	) = """{"familyId":"$familyId","visibility":"FAMILY","title":"$title","isAllDay":false,"startAt":"$startAt","endAt":"$endAt",""" +
		""""attendeeAccountIds":[${attendees.joinToString(",") { "\"$it\"" }}]}"""

	private fun privateSchedule(title: String = "헬스", startAt: String = "2026-11-10T07:00:00", endAt: String = "2026-11-10T08:00:00") =
		"""{"visibility":"PRIVATE","title":"$title","isAllDay":false,"startAt":"$startAt","endAt":"$endAt"}"""

	private fun get(uri: String, accountId: UUID = me) = mockMvc.get(uri) {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	@Test
	fun `가족 일정을 만들면 참석자와 함께 반환한다`() {
		create(familySchedule()).andExpect {
			status { isCreated() }
			jsonPath("\$.data.scheduleId") { exists() }
			jsonPath("\$.data.visibility") { value("FAMILY") }
			jsonPath("\$.data.attendeeAccountIds") { value(containsInAnyOrder(me.toString(), member.toString())) }
		}
	}

	@Test
	fun `개인 일정의 참석자는 본인이다`() {
		create(privateSchedule()).andExpect {
			status { isCreated() }
			jsonPath("\$.data.familyId") { doesNotExist() }
			jsonPath("\$.data.attendeeAccountIds.length()") { value(1) }
			jsonPath("\$.data.attendeeAccountIds[0]") { value(me.toString()) }
		}
	}

	@Test
	fun `종일 일정은 시간을 버린다`() {
		create(
			"""{"visibility":"PRIVATE","title":"여행","isAllDay":true,"startAt":"2026-11-10T13:00:00","endAt":"2026-11-12T09:30:00"}""",
		).andExpect {
			status { isCreated() }
			jsonPath("\$.data.startAt") { value("2026-11-10T00:00:00") }
			jsonPath("\$.data.endAt") { value("2026-11-12T00:00:00") }
		}
	}

	@Test
	fun `참석자와 공개 범위 검증`() {
		create(familySchedule(attendees = emptyList())).andExpect { status { isBadRequest() } }
		create(familySchedule(attendees = listOf(me, outsider))).andExpect { status { isBadRequest() } }
		create(familySchedule(), accountId = outsider).andExpect { status { isNotFound() } }
		create(
			"""{"visibility":"PRIVATE","title":"헬스","isAllDay":false,"startAt":"2026-11-10T07:00:00","endAt":"2026-11-10T08:00:00","attendeeAccountIds":["$member"]}""",
		).andExpect { status { isBadRequest() } }
		create(
			"""{"familyId":"$familyId","visibility":"PRIVATE","title":"헬스","isAllDay":false,"startAt":"2026-11-10T07:00:00","endAt":"2026-11-10T08:00:00"}""",
		).andExpect { status { isBadRequest() } }
	}

	@Test
	fun `끝이 시작보다 앞서면 400`() {
		create(privateSchedule(startAt = "2026-11-10T09:00:00", endAt = "2026-11-10T08:00:00")).andExpect { status { isBadRequest() } }
	}

	@Test
	fun `기간과 겹치는 가족 일정과 내 개인 일정만 반환한다`() {
		createId(familySchedule("전날부터 이어지는 여행", startAt = "2026-11-09T10:00:00", endAt = "2026-11-11T10:00:00"))
		createId(privateSchedule("내 헬스", startAt = "2026-11-10T07:00:00", endAt = "2026-11-10T08:00:00"))
		createId(privateSchedule("남의 헬스"), accountId = member)
		createId(familySchedule("기간 밖", startAt = "2026-11-11T00:00:00", endAt = "2026-11-11T01:00:00"))

		get("/api/v1/schedule?familyId=$familyId&from=2026-11-10&to=2026-11-10").andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(2) }
			jsonPath("\$.data[0].title") { value("전날부터 이어지는 여행") }
			jsonPath("\$.data[1].title") { value("내 헬스") }
		}
		get("/api/v1/schedule?from=2026-11-10&to=2026-11-10").andExpect { jsonPath("\$.data.length()") { value(1) } }
	}

	@Test
	fun `기간이 잘못되면 400`() {
		get("/api/v1/schedule?from=2026-11-10&to=2026-11-09").andExpect { status { isBadRequest() } }
		get("/api/v1/schedule?to=2026-11-09").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `남의 개인 일정과 다른 가족의 일정은 404`() {
		val privateId = createId(privateSchedule(), accountId = member)
		val familyScheduleId = createId(familySchedule())

		get("/api/v1/schedule/$privateId").andExpect { status { isNotFound() } }
		get("/api/v1/schedule/$familyScheduleId", accountId = outsider).andExpect { status { isNotFound() } }
	}

	@Test
	fun `수정하면 참석자를 보낸 목록으로 교체한다`() {
		val scheduleId = createId(familySchedule(attendees = listOf(me, member)))

		mockMvc.patch("/api/v1/schedule/$scheduleId") {
			header("Authorization", "Bearer ${accessToken(me)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"title":"외식 변경","isAllDay":false,"startAt":"2026-11-10T19:00:00","endAt":"2026-11-10T21:00:00","attendeeAccountIds":["$me","$member2"]}"""
		}.andExpect {
			status { isOk() }
			jsonPath("\$.data.title") { value("외식 변경") }
		}

		get("/api/v1/schedule/$scheduleId").andExpect {
			jsonPath("\$.data.attendeeAccountIds") { value(containsInAnyOrder(me.toString(), member2.toString())) }
		}
	}

	@Test
	fun `참석자가 가족을 나가도 참석자를 그대로 두면 수정할 수 있다`() {
		val scheduleId = createId(familySchedule(attendees = listOf(me, member)))
		`when`(familyClient.isMember(familyId, member)).thenReturn(false)

		mockMvc.patch("/api/v1/schedule/$scheduleId") {
			header("Authorization", "Bearer ${accessToken(me)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"title":"외식 2","isAllDay":false,"startAt":"2026-11-10T18:00:00","endAt":"2026-11-10T20:00:00","attendeeAccountIds":["$me","$member"]}"""
		}.andExpect { status { isOk() } }
	}

	@Test
	fun `삭제하면 조회되지 않는다`() {
		val scheduleId = createId(privateSchedule())

		mockMvc.delete("/api/v1/schedule/$scheduleId") { header("Authorization", "Bearer ${accessToken(me)}") }
			.andExpect { status { isNoContent() } }
		get("/api/v1/schedule/$scheduleId").andExpect { status { isNotFound() } }
	}
}
