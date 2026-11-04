package com.ieum.grocery

import com.ieum.events.FamilyEventData
import com.ieum.events.IeumEvent
import com.ieum.grocery.domain.entity.GroceryItem
import com.ieum.grocery.domain.entity.Storage
import com.ieum.grocery.domain.enums.GroceryCategory
import com.ieum.grocery.domain.enums.Visibility
import com.ieum.grocery.repository.GroceryItemRepository
import com.ieum.grocery.repository.StorageRepository
import com.ieum.grocery.service.FamilyEventReceiver
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.web.config.TimeZoneConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
class GroceryFamilyEventTest @Autowired constructor(
	private val familyEventReceiver: FamilyEventReceiver,
	private val storageRepository: StorageRepository,
	private val groceryItemRepository: GroceryItemRepository,
	private val objectMapper: ObjectMapper,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val home = UUID.randomUUID()
	private val otherHome = UUID.randomUUID()
	private val leaver = UUID.randomUUID()
	private val stayer = UUID.randomUUID()

	@BeforeEach
	fun clean() {
		groceryItemRepository.deleteAll()
		storageRepository.deleteAll()
	}

	private fun item(familyId: UUID, creator: UUID, visibility: Visibility, deleted: Boolean = false): UUID {
		val storage = storageRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId).firstOrNull()
			?: storageRepository.save(Storage(familyId = familyId, name = "냉장고"))
		return requireNotNull(
			groceryItemRepository.save(
				GroceryItem(
					storageId = requireNotNull(storage.storageId),
					familyId = familyId,
					creatorAccountId = creator,
					name = "요거트",
					category = GroceryCategory.DAIRY,
					visibility = visibility,
					quantity = 1,
					purchasePrice = null,
					purchaseDate = null,
					expiryDate = null,
				).apply { if (deleted) delete() },
			).groceryItemId,
		)
	}

	// family 는 이벤트 시각을 서울 시계로 찍는다.
	private fun seoulNow() = LocalDateTime.now(Clock.system(TimeZoneConfig.SEOUL))

	private fun send(type: String, data: FamilyEventData, createdAt: LocalDateTime = seoulNow().plusMinutes(1)) =
		familyEventReceiver.receive(objectMapper.writeValueAsString(IeumEvent(UUID.randomUUID(), type, createdAt, data)))

	private fun visibilityOf(itemId: UUID) = groceryItemRepository.findById(itemId).get().visibility

	@Test
	fun `가족을 떠난 사람의 그 가족 개인 재고만 가족 재고로 바뀐다`() {
		val leaversPrivate = item(home, leaver, Visibility.PRIVATE)
		val stayersPrivate = item(home, stayer, Visibility.PRIVATE)
		val leaversOtherHome = item(otherHome, leaver, Visibility.PRIVATE)
		val leaversDeleted = item(home, leaver, Visibility.PRIVATE, deleted = true)

		send("FAMILY_MEMBER_LEFT", FamilyEventData(familyId = home, accountId = leaver))

		assertEquals(
			listOf(Visibility.FAMILY, Visibility.PRIVATE, Visibility.PRIVATE, Visibility.PRIVATE),
			listOf(leaversPrivate, stayersPrivate, leaversOtherHome, leaversDeleted).map { visibilityOf(it) },
		)
	}

	@Test
	fun `같은 이벤트가 두 번 와도 결과가 같다`() {
		val leaversPrivate = item(home, leaver, Visibility.PRIVATE)

		repeat(2) { send("FAMILY_MEMBER_LEFT", FamilyEventData(familyId = home, accountId = leaver)) }

		assertEquals(Visibility.FAMILY, visibilityOf(leaversPrivate))
	}

	@Test
	fun `떠난 뒤에 다시 들어와 만든 개인 재고는 늦게 온 이벤트로 바뀌지 않는다`() {
		val afterRejoin = item(home, leaver, Visibility.PRIVATE)

		send("FAMILY_MEMBER_LEFT", FamilyEventData(familyId = home, accountId = leaver), createdAt = seoulNow().minusMinutes(1))

		assertEquals(Visibility.PRIVATE, visibilityOf(afterRejoin))
	}

	@Test
	fun `가족이 삭제되면 그 가족의 보관 장소와 재고를 지운다`() {
		val homeItem = item(home, stayer, Visibility.FAMILY)
		val otherItem = item(otherHome, stayer, Visibility.FAMILY)

		send("FAMILY_DELETED", FamilyEventData(familyId = home))

		assertEquals(true to false, groceryItemRepository.findById(homeItem).get().isDeleted to groceryItemRepository.findById(otherItem).get().isDeleted)
		assertEquals(emptyList(), storageRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(home))
		assertEquals(1, storageRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(otherHome).size)
	}

	@Test
	fun `모르는 이벤트는 건너뛴다`() {
		val leaversPrivate = item(home, leaver, Visibility.PRIVATE)

		send("FAMILY_RENAMED", FamilyEventData(familyId = home, accountId = leaver))

		assertEquals(Visibility.PRIVATE, visibilityOf(leaversPrivate))
	}
}
