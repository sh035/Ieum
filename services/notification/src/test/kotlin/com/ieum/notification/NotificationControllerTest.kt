package com.ieum.notification

import com.ieum.events.NotificationEventType
import com.ieum.events.NotificationTargetType
import com.ieum.grpc.client.account.AccountClient
import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.notification.domain.entity.Notification
import com.ieum.notification.repository.NotificationRepository
import com.ieum.web.exception.IeumException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val notificationRepository: NotificationRepository,
) : PostgresKafkaTestSupport() {

	@MockitoBean
	private lateinit var accountClient: AccountClient

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val me = UUID.randomUUID()
	private val other = UUID.randomUUID()
	private val familyId = UUID.randomUUID()
	private val otherFamilyId = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		notificationRepository.deleteAllInBatch()
		`when`(familyClient.isMember(familyId, me)).thenReturn(true)
		`when`(familyClient.getFamilyIds(me)).thenReturn(listOf(familyId))
	}

	private fun save(accountId: UUID = me, familyId: UUID? = this.familyId, title: String = "알림") =
		notificationRepository.save(
			Notification(
				accountId = accountId,
				familyId = familyId,
				type = NotificationEventType.TODO_ASSIGNED,
				title = title,
				body = "분리수거하기",
				targetType = NotificationTargetType.TODO,
				targetId = UUID.randomUUID(),
				dedupKey = UUID.randomUUID().toString(),
			),
		)

	private fun list(tab: String, familyId: UUID? = null, page: Int = 1, size: Int = 20, accountId: UUID = me) =
		mockMvc.get("/api/v1/notification") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			param("tab", tab)
			familyId?.let { param("familyId", it.toString()) }
			param("page", page.toString())
			param("size", size.toString())
		}

	private fun unreadCount(tab: String, familyId: UUID? = null) = mockMvc.get("/api/v1/notification/unread-count") {
		header("Authorization", "Bearer ${accessToken(me)}")
		param("tab", tab)
		familyId?.let { param("familyId", it.toString()) }
	}

	private fun markRead(notificationId: UUID?, accountId: UUID = me) =
		mockMvc.post("/api/v1/notification/$notificationId/read") { header("Authorization", "Bearer ${accessToken(accountId)}") }

	private fun markUnread(notificationId: UUID?) =
		mockMvc.delete("/api/v1/notification/$notificationId/read") { header("Authorization", "Bearer ${accessToken(me)}") }

	@Test
	fun `탭마다 내 알림만 최신순으로 보여준다`() {
		save(title = "가족")
		save(familyId = null, title = "개인")
		save(accountId = other, title = "남의 알림")

		list("ALL").andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(2) }
			jsonPath("\$.data[0].title") { value("개인") }
			jsonPath("\$.data[1].title") { value("가족") }
			jsonPath("\$.totalCount") { value(2) }
		}
		list("FAMILY", familyId).andExpect {
			jsonPath("\$.data.length()") { value(1) }
			jsonPath("\$.data[0].title") { value("가족") }
			jsonPath("\$.data[0].familyId") { value(familyId.toString()) }
			jsonPath("\$.data[0].type") { value("TODO_ASSIGNED") }
			jsonPath("\$.data[0].targetType") { value("TODO") }
			jsonPath("\$.data[0].isRead") { value(false) }
		}
		list("PERSONAL").andExpect {
			jsonPath("\$.data.length()") { value(1) }
			jsonPath("\$.data[0].title") { value("개인") }
		}
	}

	@Test
	fun `전체 탭은 떠난 가족의 알림을 빼고 지금 가족과 개인 알림만 보여준다`() {
		save(title = "지금 가족")
		save(familyId = null, title = "개인")
		val left = save(familyId = otherFamilyId, title = "떠난 가족")

		list("ALL").andExpect {
			jsonPath("\$.data.length()") { value(2) }
			jsonPath("\$.data[0].title") { value("개인") }
			jsonPath("\$.data[1].title") { value("지금 가족") }
			jsonPath("\$.totalCount") { value(2) }
		}
		unreadCount("ALL").andExpect { jsonPath("\$.data.count") { value(2) } }

		mockMvc.post("/api/v1/notification/read-all") {
			header("Authorization", "Bearer ${accessToken(me)}")
			param("tab", "ALL")
		}.andExpect { status { isNoContent() } }

		unreadCount("ALL").andExpect { jsonPath("\$.data.count") { value(0) } }
		assertEquals(false, notificationRepository.findById(requireNotNull(left.notificationId)).get().isRead)
	}

	@Test
	fun `가족이 없어도 전체 탭에 개인 알림은 나온다`() {
		`when`(familyClient.getFamilyIds(me)).thenReturn(emptyList())
		save(familyId = null, title = "개인")
		save(title = "떠난 가족")

		list("ALL").andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(1) }
			jsonPath("\$.data[0].title") { value("개인") }
		}
	}

	@Test
	fun `family 를 부를 수 없으면 전체 탭은 503 이고 개인 탭은 그대로 나온다`() {
		`when`(familyClient.getFamilyIds(me)).thenThrow(IeumException(GrpcClientErrorCode.FAMILY_SERVICE_UNAVAILABLE))
		save(familyId = null, title = "개인")

		list("ALL").andExpect { status { isServiceUnavailable() } }
		unreadCount("ALL").andExpect { status { isServiceUnavailable() } }
		list("PERSONAL").andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(1) }
		}
	}

	@Test
	fun `페이지 정보를 함께 준다`() {
		repeat(21) { save() }

		list("ALL", page = 2, size = 20).andExpect {
			jsonPath("\$.data.length()") { value(1) }
			jsonPath("\$.totalCount") { value(21) }
			jsonPath("\$.totalPage") { value(2) }
			jsonPath("\$.currentPage") { value(2) }
			jsonPath("\$.size") { value(20) }
		}
		list("ALL", page = 0).andExpect { status { isBadRequest() } }
		list("ALL", size = 101).andExpect { status { isBadRequest() } }
	}

	@Test
	fun `가족 탭은 familyId 가 없으면 400 이고 구성원이 아니면 404`() {
		list("FAMILY").andExpect { status { isBadRequest() } }
		list("FAMILY", otherFamilyId).andExpect { status { isNotFound() } }
		unreadCount("FAMILY", otherFamilyId).andExpect { status { isNotFound() } }
	}

	@Test
	fun `안 읽은 개수와 읽음 안 읽음 전환`() {
		val first = save()
		save(familyId = null)

		unreadCount("ALL").andExpect { jsonPath("\$.data.count") { value(2) } }
		markRead(first.notificationId).andExpect { status { isNoContent() } }
		unreadCount("ALL").andExpect { jsonPath("\$.data.count") { value(1) } }
		unreadCount("FAMILY", familyId).andExpect { jsonPath("\$.data.count") { value(0) } }
		markUnread(first.notificationId).andExpect { status { isNoContent() } }
		unreadCount("FAMILY", familyId).andExpect { jsonPath("\$.data.count") { value(1) } }
	}

	@Test
	fun `모두 읽음은 고른 탭만 읽는다`() {
		save()
		save(familyId = null)

		mockMvc.post("/api/v1/notification/read-all") {
			header("Authorization", "Bearer ${accessToken(me)}")
			param("tab", "PERSONAL")
		}.andExpect { status { isNoContent() } }

		unreadCount("PERSONAL").andExpect { jsonPath("\$.data.count") { value(0) } }
		unreadCount("FAMILY", familyId).andExpect { jsonPath("\$.data.count") { value(1) } }
		assertEquals(me, notificationRepository.findAll().single { it.familyId == null }.updatedBy)
	}

	@Test
	fun `남의 알림이나 지운 알림은 404 이고 지운 알림은 목록에서 빠진다`() {
		val mine = save()
		val others = save(accountId = other)

		markRead(others.notificationId).andExpect { status { isNotFound() } }
		mockMvc.delete("/api/v1/notification/${others.notificationId}") {
			header("Authorization", "Bearer ${accessToken(me)}")
		}.andExpect { status { isNotFound() } }

		mockMvc.delete("/api/v1/notification/${mine.notificationId}") {
			header("Authorization", "Bearer ${accessToken(me)}")
		}.andExpect { status { isNoContent() } }
		markRead(mine.notificationId).andExpect { status { isNotFound() } }
		list("ALL").andExpect { jsonPath("\$.data.length()") { value(0) } }
		assertEquals(false, notificationRepository.findById(requireNotNull(others.notificationId)).get().isRead)
	}
}
