package com.ieum.grocery

import com.ieum.grocery.domain.entity.Storage
import com.ieum.grocery.repository.GroceryItemRepository
import com.ieum.grocery.repository.StorageRepository
import com.ieum.grpc.client.GrpcClientErrorCode
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.web.exception.IeumException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class GroceryItemControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val storageRepository: StorageRepository,
	private val groceryItemRepository: GroceryItemRepository,
	private val objectMapper: ObjectMapper,
) : PostgresTestSupport() {

	@MockitoBean
	private lateinit var familyClient: FamilyClient

	private val familyId = UUID.randomUUID()
	private val otherFamilyId = UUID.randomUUID()
	private val me = UUID.randomUUID()
	private val member = UUID.randomUUID()
	private val outsider = UUID.randomUUID()

	private lateinit var fridgeId: UUID
	private lateinit var freezerId: UUID
	private lateinit var otherFridgeId: UUID

	@BeforeEach
	fun setUp() {
		groceryItemRepository.deleteAllInBatch()
		storageRepository.deleteAllInBatch()
		listOf(me, member).forEach { `when`(familyClient.isMember(familyId, it)).thenReturn(true) }
		`when`(familyClient.isMember(otherFamilyId, outsider)).thenReturn(true)
		fridgeId = requireNotNull(storageRepository.save(Storage(familyId = familyId, name = "냉장고")).storageId)
		freezerId = requireNotNull(storageRepository.save(Storage(familyId = familyId, name = "냉동고")).storageId)
		otherFridgeId = requireNotNull(storageRepository.save(Storage(familyId = otherFamilyId, name = "냉장고")).storageId)
	}

	private fun create(body: String, accountId: UUID = me) = mockMvc.post("/api/v1/grocery/item") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private fun createId(body: String, accountId: UUID = me): String {
		val response = create(body, accountId).andReturn().response.contentAsString
		return objectMapper.readTree(response).get("data").get("groceryItemId").asString()
	}

	private fun item(
		name: String = "우유",
		visibility: String = "FAMILY",
		storageId: UUID = fridgeId,
		expiryDate: String? = null,
		extra: String = "",
	): String {
		val expiry = expiryDate?.let { ""","expiryDate":"$it"""" } ?: ""
		return """{"storageId":"$storageId","name":"$name","category":"DAIRY","visibility":"$visibility"$expiry$extra}"""
	}

	private fun get(uri: String, accountId: UUID = me) = mockMvc.get(uri) {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	private fun update(itemId: String, body: String, accountId: UUID = me) = mockMvc.patch("/api/v1/grocery/item/$itemId") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	@Test
	fun `물건을 추가하면 가족은 장소에서 가져오고 개수는 기본 1이다`() {
		create(item(extra = ""","purchasePrice":2800,"purchaseDate":"2026-10-28"""")).andExpect {
			status { isCreated() }
			jsonPath("\$.data.groceryItemId") { exists() }
			jsonPath("\$.data.familyId") { value(familyId.toString()) }
			jsonPath("\$.data.storageId") { value(fridgeId.toString()) }
			jsonPath("\$.data.creatorAccountId") { value(me.toString()) }
			jsonPath("\$.data.quantity") { value(1) }
			jsonPath("\$.data.purchasePrice") { value(2800) }
			jsonPath("\$.data.expiryDate") { value(null) }
		}
	}

	@Test
	fun `볼 수 없거나 없는 장소에는 추가할 수 없다`() {
		create(item(storageId = otherFridgeId)).andExpect { status { isNotFound() } }
		create(item(storageId = UUID.randomUUID())).andExpect { status { isNotFound() } }
	}

	@Test
	fun `잘못된 입력은 400`() {
		create(item(name = " ")).andExpect { status { isBadRequest() } }
		create(item(extra = ""","quantity":0""")).andExpect { status { isBadRequest() } }
		create(item(extra = ""","purchasePrice":-1""")).andExpect { status { isBadRequest() } }
		create("""{"storageId":"$fridgeId","name":"우유","category":"TOY","visibility":"FAMILY"}""")
			.andExpect { status { isBadRequest() } }
	}

	@Test
	fun `목록은 가족 물건과 내 개인 물건을 유통기한이 가까운 순으로 반환한다`() {
		createId(item("기한 없음"))
		createId(item("늦은 기한", expiryDate = "2026-11-20"))
		createId(item("내 개인 간식", visibility = "PRIVATE", storageId = freezerId, expiryDate = "2026-11-01"))
		createId(item("가족 개인 간식", visibility = "PRIVATE", expiryDate = "2026-10-30"), member)
		createId(item("남의 집 우유", storageId = otherFridgeId), outsider)

		get("/api/v1/grocery/item?familyId=$familyId").andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(3) }
			jsonPath("\$.data[0].name") { value("내 개인 간식") }
			jsonPath("\$.data[0].visibility") { value("PRIVATE") }
			jsonPath("\$.data[1].name") { value("늦은 기한") }
			jsonPath("\$.data[2].name") { value("기한 없음") }
		}
	}

	@Test
	fun `장소로 걸러 보고 다른 가족 장소나 비구성원은 404`() {
		createId(item("우유"))
		createId(item("만두", storageId = freezerId))

		get("/api/v1/grocery/item?familyId=$familyId&storageId=$freezerId").andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(1) }
			jsonPath("\$.data[0].name") { value("만두") }
		}
		get("/api/v1/grocery/item?familyId=$familyId&storageId=$otherFridgeId").andExpect { status { isNotFound() } }
		get("/api/v1/grocery/item?familyId=$familyId", outsider).andExpect { status { isNotFound() } }
	}

	@Test
	fun `가족 물건은 구성원 누구나 보고 개인 물건은 만든 사람만 본다`() {
		val familyItemId = createId(item("우유"))
		val privateItemId = createId(item("내 간식", visibility = "PRIVATE"))

		get("/api/v1/grocery/item/$familyItemId", member).andExpect { status { isOk() } }
		get("/api/v1/grocery/item/$familyItemId", outsider).andExpect { status { isNotFound() } }
		get("/api/v1/grocery/item/$privateItemId", member).andExpect { status { isNotFound() } }
		get("/api/v1/grocery/item/$privateItemId").andExpect { status { isOk() } }
	}

	@Test
	fun `가족을 떠나면 그 가족에 둔 내 개인 물건도 보거나 고칠 수 없다`() {
		val privateItemId = createId(item("내 간식", visibility = "PRIVATE"))
		`when`(familyClient.isMember(familyId, me)).thenReturn(false)

		get("/api/v1/grocery/item/$privateItemId").andExpect { status { isNotFound() } }
		update(privateItemId, """{"storageId":"$fridgeId","name":"내 간식","category":"DAIRY","quantity":1}""")
			.andExpect { status { isNotFound() } }
		mockMvc.delete("/api/v1/grocery/item/$privateItemId") { header("Authorization", "Bearer ${accessToken(me)}") }
			.andExpect { status { isNotFound() } }
	}

	@Test
	fun `수정은 보낸 값으로 바꾸고 null 은 지우며 같은 가족 장소로만 옮긴다`() {
		val itemId = createId(item("우유", expiryDate = "2026-11-04", extra = ""","purchasePrice":2800"""))

		update(
			itemId,
			"""{"storageId":"$freezerId","name":"냉동 우유","category":"FROZEN","quantity":3,"purchasePrice":null}""",
			member,
		).andExpect {
			status { isOk() }
			jsonPath("\$.data.storageId") { value(freezerId.toString()) }
			jsonPath("\$.data.name") { value("냉동 우유") }
			jsonPath("\$.data.category") { value("FROZEN") }
			jsonPath("\$.data.quantity") { value(3) }
			jsonPath("\$.data.purchasePrice") { value(null) }
			jsonPath("\$.data.expiryDate") { value(null) }
		}
		update(itemId, """{"storageId":"$otherFridgeId","name":"우유","category":"DAIRY","quantity":1}""")
			.andExpect { status { isNotFound() } }
		update(itemId, """{"storageId":"$fridgeId","name":"우유","category":"DAIRY","quantity":0}""")
			.andExpect { status { isBadRequest() } }
	}

	@Test
	fun `삭제하면 조회되지 않고 빈 장소는 지울 수 있다`() {
		val itemId = createId(item("만두", storageId = freezerId))

		mockMvc.delete("/api/v1/grocery/item/$itemId") { header("Authorization", "Bearer ${accessToken(me)}") }
			.andExpect { status { isNoContent() } }
		get("/api/v1/grocery/item/$itemId").andExpect { status { isNotFound() } }
		mockMvc.delete("/api/v1/grocery/storage/$freezerId") { header("Authorization", "Bearer ${accessToken(me)}") }
			.andExpect { status { isNoContent() } }
	}

	private fun briefing(accountId: UUID = me) = get("/api/v1/grocery/daily-briefing", accountId)

	@Test
	fun `데일리 브리핑은 지남 오늘 내일 순으로 상태와 함께 돌려준다`() {
		val today = LocalDate.now()
		`when`(familyClient.getFamilyIds(me)).thenReturn(listOf(familyId))
		createId(item("내일 우유", expiryDate = today.plusDays(1).toString()))
		createId(item("오늘 두부", expiryDate = today.toString()))
		createId(item("오늘 계란", expiryDate = today.toString()))
		createId(item("지난 치즈", expiryDate = today.minusDays(5).toString()))

		briefing().andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(4) }
			jsonPath("\$.data[0].name") { value("지난 치즈") }
			jsonPath("\$.data[0].expiryStatus") { value("EXPIRED") }
			jsonPath("\$.data[1].name") { value("오늘 두부") }
			jsonPath("\$.data[1].expiryStatus") { value("TODAY") }
			jsonPath("\$.data[2].name") { value("오늘 계란") }
			jsonPath("\$.data[3].name") { value("내일 우유") }
			jsonPath("\$.data[3].expiryStatus") { value("TOMORROW") }
		}
	}

	@Test
	fun `데일리 브리핑에서 모레 이후 기한 없음 삭제한 재고는 빠진다`() {
		val today = LocalDate.now()
		`when`(familyClient.getFamilyIds(me)).thenReturn(listOf(familyId))
		createId(item("모레", expiryDate = today.plusDays(2).toString()))
		createId(item("기한 없음"))
		val deletedId = createId(item("삭제", expiryDate = today.toString()))
		mockMvc.delete("/api/v1/grocery/item/$deletedId") { header("Authorization", "Bearer ${accessToken(me)}") }

		briefing().andExpect {
			status { isOk() }
			jsonPath("\$.data") { isEmpty() }
		}
	}

	@Test
	fun `데일리 브리핑은 가족 재고와 내 개인 재고만 돌려준다`() {
		val today = LocalDate.now().toString()
		`when`(familyClient.getFamilyIds(me)).thenReturn(listOf(familyId))
		createId(item("가족 재고", expiryDate = today), accountId = member)
		createId(item("내 개인", visibility = "PRIVATE", expiryDate = today))
		createId(item("남의 개인", visibility = "PRIVATE", expiryDate = today), accountId = member)

		briefing().andExpect {
			jsonPath("\$.data.length()") { value(2) }
			jsonPath("\$.data[0].name") { value("가족 재고") }
			jsonPath("\$.data[1].name") { value("내 개인") }
		}
	}

	@Test
	fun `떠난 가족의 재고는 내 개인 재고여도 나오지 않고 가족이 없으면 빈 목록이다`() {
		val today = LocalDate.now().toString()
		createId(item("가족 재고", expiryDate = today))
		createId(item("내 개인", visibility = "PRIVATE", expiryDate = today))
		`when`(familyClient.getFamilyIds(me)).thenReturn(emptyList())

		briefing().andExpect {
			status { isOk() }
			jsonPath("\$.data") { isEmpty() }
		}
	}

	@Test
	fun `데일리 브리핑은 family 를 부를 수 없으면 503`() {
		`when`(familyClient.getFamilyIds(me)).thenThrow(IeumException(GrpcClientErrorCode.FAMILY_SERVICE_UNAVAILABLE))

		briefing().andExpect { status { isServiceUnavailable() } }
	}
}
