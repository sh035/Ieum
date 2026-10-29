package com.ieum.grocery

import com.ieum.grocery.domain.entity.GroceryItem
import com.ieum.grocery.domain.enums.GroceryCategory
import com.ieum.grocery.domain.enums.Visibility
import com.ieum.grocery.repository.GroceryItemRepository
import com.ieum.grocery.repository.StorageRepository
import com.ieum.grpc.client.family.FamilyClient
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
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class StorageControllerTest @Autowired constructor(
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
	private val outsider = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		groceryItemRepository.deleteAllInBatch()
		storageRepository.deleteAllInBatch()
		`when`(familyClient.isMember(familyId, me)).thenReturn(true)
		`when`(familyClient.isMember(otherFamilyId, outsider)).thenReturn(true)
	}

	private fun create(names: List<String>, family: UUID = familyId, accountId: UUID = me) =
		mockMvc.post("/api/v1/grocery/storage") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			contentType = MediaType.APPLICATION_JSON
			content = objectMapper.writeValueAsString(mapOf("familyId" to family, "names" to names))
		}

	private fun createId(name: String, family: UUID = familyId, accountId: UUID = me): String {
		val response = create(listOf(name), family, accountId).andReturn().response.contentAsString
		return objectMapper.readTree(response).get("data").get(0).get("storageId").asString()
	}

	private fun list(family: UUID = familyId, accountId: UUID = me) = mockMvc.get("/api/v1/grocery/storage") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
		param("familyId", family.toString())
	}

	private fun rename(storageId: String, name: String, accountId: UUID = me) =
		mockMvc.patch("/api/v1/grocery/storage/$storageId") {
			header("Authorization", "Bearer ${accessToken(accountId)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"name":"$name"}"""
		}

	private fun delete(storageId: String, accountId: UUID = me) = mockMvc.delete("/api/v1/grocery/storage/$storageId") {
		header("Authorization", "Bearer ${accessToken(accountId)}")
	}

	@Test
	fun `여러 장소를 한 번에 만들고 요청 안의 중복과 공백은 정리한다`() {
		create(listOf("주방 냉장고", " 냉동고 ", "냉동고")).andExpect {
			status { isCreated() }
			jsonPath("\$.data.length()") { value(2) }
			jsonPath("\$.data[0].name") { value("주방 냉장고") }
			jsonPath("\$.data[0].familyId") { value(familyId.toString()) }
			jsonPath("\$.data[1].name") { value("냉동고") }
		}
	}

	@Test
	fun `가족의 장소만 만든 순서대로 조회한다`() {
		create(listOf("주방 냉장고", "냉동고"))
		createId("남의 냉장고", otherFamilyId, outsider)

		list().andExpect {
			status { isOk() }
			jsonPath("\$.data.length()") { value(2) }
			jsonPath("\$.data[0].name") { value("주방 냉장고") }
			jsonPath("\$.data[1].name") { value("냉동고") }
		}
	}

	@Test
	fun `가족 안에 같은 이름이 있으면 409`() {
		createId("냉동고")

		create(listOf("김치냉장고", "냉동고")).andExpect { status { isConflict() } }
		list().andExpect { jsonPath("\$.data.length()") { value(1) } }
	}

	@Test
	fun `다른 가족과는 이름이 겹쳐도 된다`() {
		createId("냉동고", otherFamilyId, outsider)

		create(listOf("냉동고")).andExpect { status { isCreated() } }
	}

	@Test
	fun `구성원이 아니면 생성과 조회 모두 404`() {
		create(listOf("냉동고"), accountId = outsider).andExpect { status { isNotFound() } }
		list(accountId = outsider).andExpect { status { isNotFound() } }
	}

	@Test
	fun `이름이 비었거나 너무 길거나 목록이 비면 400`() {
		create(listOf(" ")).andExpect { status { isBadRequest() } }
		create(listOf("가".repeat(51))).andExpect { status { isBadRequest() } }
		create(emptyList()).andExpect { status { isBadRequest() } }
		create((1..11).map { "장소$it" }).andExpect { status { isBadRequest() } }
	}

	@Test
	fun `이름을 바꾸고 이미 있는 이름이면 409`() {
		val fridgeId = createId("냉장고")
		createId("냉동고")

		rename(fridgeId, "주방 냉장고").andExpect {
			status { isOk() }
			jsonPath("\$.data.name") { value("주방 냉장고") }
		}
		rename(fridgeId, "냉동고").andExpect { status { isConflict() } }
	}

	@Test
	fun `다른 가족의 장소는 수정과 삭제 모두 404`() {
		val otherId = createId("남의 냉장고", otherFamilyId, outsider)

		rename(otherId, "내 냉장고").andExpect { status { isNotFound() } }
		delete(otherId).andExpect { status { isNotFound() } }
		delete(UUID.randomUUID().toString()).andExpect { status { isNotFound() } }
	}

	@Test
	fun `빈 장소는 삭제하고 같은 이름으로 다시 만들 수 있다`() {
		val freezerId = createId("냉동고")

		delete(freezerId).andExpect { status { isNoContent() } }
		list().andExpect { jsonPath("\$.data.length()") { value(0) } }
		create(listOf("냉동고")).andExpect { status { isCreated() } }
	}

	@Test
	fun `물건이 남아 있으면 삭제할 수 없다`() {
		val freezerId = createId("냉동고")
		groceryItemRepository.save(
			GroceryItem(
				storageId = UUID.fromString(freezerId),
				familyId = familyId,
				creatorAccountId = outsider,
				name = "만두",
				category = GroceryCategory.FROZEN,
				visibility = Visibility.PRIVATE,
				quantity = 1,
				purchasePrice = null,
				purchaseDate = null,
				expiryDate = null,
			),
		)

		delete(freezerId).andExpect { status { isConflict() } }
	}
}
