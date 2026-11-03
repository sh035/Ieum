package com.ieum.grocery

import com.ieum.grocery.domain.entity.GroceryItem
import com.ieum.grocery.domain.entity.Storage
import com.ieum.grocery.domain.enums.GroceryCategory
import com.ieum.grocery.domain.enums.Visibility
import com.ieum.grocery.repository.GroceryItemRepository
import com.ieum.grocery.repository.StorageRepository
import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.grpc.client.family.FamilyInfo
import com.ieum.grpc.client.family.FamilyRole
import com.ieum.proto.grocery.v1.GetBriefingCountsRequest
import com.ieum.proto.grocery.v1.GroceryInternalServiceGrpc
import com.ieum.web.exception.IeumException
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.grpc.client.GrpcChannelFactory
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@AutoConfigureTestGrpcTransport
class GroceryInternalGrpcControllerTest @Autowired constructor(
	channels: GrpcChannelFactory,
	private val storageRepository: StorageRepository,
	private val groceryItemRepository: GroceryItemRepository,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val stub = GroceryInternalServiceGrpc.newBlockingStub(channels.createChannel("grocery"))
	private val home = UUID.randomUUID()
	private val grandmaHome = UUID.randomUUID()
	private val mom = UUID.randomUUID()
	private val dad = UUID.randomUUID()
	private val kid = UUID.randomUUID()
	private val grandma = UUID.randomUUID()
	private val today = LocalDate.of(2026, 10, 30)

	@BeforeEach
	fun setUp() {
		groceryItemRepository.deleteAll()
		storageRepository.deleteAll()
		`when`(familyClient.getFamily(home))
			.thenReturn(FamilyInfo("우리집", mapOf(mom to FamilyRole.OWNER, dad to FamilyRole.MANAGER, kid to FamilyRole.MEMBER)))
		`when`(familyClient.getFamily(grandmaHome)).thenReturn(FamilyInfo("할머니댁", mapOf(grandma to FamilyRole.OWNER, mom to FamilyRole.MEMBER)))
	}

	private fun countsOn(date: String) =
		stub.getBriefingCounts(GetBriefingCountsRequest.newBuilder().setDate(date).build()).expiringMap

	private fun item(familyId: UUID, creator: UUID, expiryDate: LocalDate?, visibility: Visibility = Visibility.FAMILY, deleted: Boolean = false) {
		val storage = storageRepository.findAll().firstOrNull { it.familyId == familyId }
			?: storageRepository.save(Storage(familyId = familyId, name = "냉장고"))
		groceryItemRepository.save(
			GroceryItem(
				storageId = requireNotNull(storage.storageId),
				familyId = familyId,
				creatorAccountId = creator,
				name = "우유",
				category = GroceryCategory.entries.first(),
				visibility = visibility,
				quantity = 1,
				purchasePrice = null,
				purchaseDate = null,
				expiryDate = expiryDate,
			).apply { if (deleted) delete() },
		)
	}

	@Test
	fun `유통기한이 다음 날까지인 재고를 볼 수 있는 사람별로 센다`() {
		item(home, creator = mom, expiryDate = today.plusDays(1))
		item(home, creator = mom, expiryDate = today.minusDays(5))
		item(home, creator = dad, expiryDate = today, visibility = Visibility.PRIVATE)
		item(home, creator = mom, expiryDate = today.plusDays(2))
		item(home, creator = mom, expiryDate = null)
		item(home, creator = mom, expiryDate = today, deleted = true)
		item(grandmaHome, creator = grandma, expiryDate = today)

		assertEquals(
			mapOf(mom.toString() to 3, dad.toString() to 3, kid.toString() to 2, grandma.toString() to 1),
			countsOn("2026-10-30"),
		)
	}

	@Test
	fun `같은 가족 재고가 여러 개여도 가족 조회는 한 번이다`() {
		item(home, creator = mom, expiryDate = today)
		item(home, creator = dad, expiryDate = today)

		countsOn("2026-10-30")

		verify(familyClient, times(1)).getFamily(home)
	}

	@Test
	fun `없는 가족의 재고는 건너뛰고 나머지는 센다`() {
		`when`(familyClient.getFamily(home)).thenReturn(null)
		item(home, creator = mom, expiryDate = today)
		item(home, creator = dad, expiryDate = today, visibility = Visibility.PRIVATE)
		item(grandmaHome, creator = grandma, expiryDate = today)

		assertEquals(mapOf(dad.toString() to 1, grandma.toString() to 1, mom.toString() to 1), countsOn("2026-10-30"))
	}

	@Test
	fun `가족 조회가 실패하면 일부만 세지 않고 실패로 응답한다`() {
		`when`(familyClient.getFamily(home)).thenThrow(IeumException(GrpcClientErrorCode.FAMILY_SERVICE_UNAVAILABLE))
		item(home, creator = mom, expiryDate = today)
		item(grandmaHome, creator = grandma, expiryDate = today)

		val e = assertFailsWith<StatusRuntimeException> { countsOn("2026-10-30") }

		assertEquals(Status.Code.UNKNOWN, e.status.code)
	}

	@Test
	fun `날짜 형식이 틀리면 INVALID_ARGUMENT`() {
		val e = assertFailsWith<StatusRuntimeException> { countsOn("20261030") }

		assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
	}
}
