package com.ieum.grocery.service

import com.ieum.grocery.controller.dto.request.CreateGroceryItemRequest
import com.ieum.grocery.controller.dto.request.UpdateGroceryItemRequest
import com.ieum.grocery.controller.dto.response.GroceryItemResponse
import com.ieum.grocery.domain.entity.GroceryItem
import com.ieum.grocery.domain.enums.Visibility
import com.ieum.grocery.exception.GroceryErrorCode
import com.ieum.grocery.repository.GroceryItemRepository
import com.ieum.grocery.repository.StorageRepository
import com.ieum.web.exception.IeumException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class GroceryItemService(
	private val groceryItemRepository: GroceryItemRepository,
	private val storageRepository: StorageRepository,
	private val storageService: StorageService,
	private val familyClient: FamilyClient,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// 가족은 요청에서 받지 않고 장소에서 가져온다.
	fun createItem(accountId: String, request: CreateGroceryItemRequest): GroceryItemResponse {
		val me = UUID.fromString(accountId)
		val storage = storageService.findAccessibleStorage(me, request.storageId)

		val item = groceryItemRepository.save(
			GroceryItem(
				storageId = request.storageId,
				familyId = storage.familyId,
				creatorAccountId = me,
				name = request.name.trim(),
				category = request.category,
				visibility = request.visibility,
				quantity = request.quantity,
				purchasePrice = request.purchasePrice,
				purchaseDate = request.purchaseDate,
				expiryDate = request.expiryDate,
			),
		)

		log.debug("Grocery item created. groceryItemId={}, accountId={}", item.groceryItemId, accountId)

		return item.toResponse()
	}

	// 가족 물건과 그 가족 안의 내 개인 물건을 유통기한이 가까운 순으로 반환한다.
	@Transactional(readOnly = true)
	fun getItems(accountId: String, familyId: UUID, storageId: UUID?): List<GroceryItemResponse> {
		val me = UUID.fromString(accountId)
		if (!familyClient.isMember(familyId, me)) {
			throw IeumException(GroceryErrorCode.FAMILY_NOT_FOUND)
		}
		storageId?.let {
			if (storageRepository.findByStorageIdAndIsDeletedFalse(it)?.familyId != familyId) {
				throw IeumException(GroceryErrorCode.STORAGE_NOT_FOUND)
			}
		}

		val familyItems = groceryItemRepository.findAllByFamilyIdAndVisibilityAndIsDeletedFalse(familyId, Visibility.FAMILY)
		val privateItems = groceryItemRepository
			.findAllByFamilyIdAndVisibilityAndCreatorAccountIdAndIsDeletedFalse(familyId, Visibility.PRIVATE, me)

		return (familyItems + privateItems)
			.filter { storageId == null || it.storageId == storageId }
			.sortedWith(compareBy<GroceryItem, LocalDate?>(nullsLast()) { it.expiryDate }.thenBy { it.createdAt })
			.map { it.toResponse() }
	}

	fun getItem(accountId: String, groceryItemId: UUID): GroceryItemResponse =
		findAccessibleItem(UUID.fromString(accountId), groceryItemId).toResponse()

	fun updateItem(accountId: String, groceryItemId: UUID, request: UpdateGroceryItemRequest): GroceryItemResponse {
		val item = findAccessibleItem(UUID.fromString(accountId), groceryItemId)

		// 다른 가족의 장소로는 옮길 수 없다.
		if (request.storageId != item.storageId &&
			storageRepository.findByStorageIdAndIsDeletedFalse(request.storageId)?.familyId != item.familyId
		) {
			throw IeumException(GroceryErrorCode.STORAGE_NOT_FOUND)
		}
		item.storageId = request.storageId
		item.name = request.name.trim()
		item.category = request.category
		item.quantity = request.quantity
		item.purchasePrice = request.purchasePrice
		item.purchaseDate = request.purchaseDate
		item.expiryDate = request.expiryDate

		return groceryItemRepository.save(item).toResponse()
	}

	fun deleteItem(accountId: String, groceryItemId: UUID) {
		val item = findAccessibleItem(UUID.fromString(accountId), groceryItemId)
		item.delete()
		groceryItemRepository.save(item)
	}

	// 볼 수 없는 물건은 존재 자체를 숨긴다.
	private fun findAccessibleItem(me: UUID, groceryItemId: UUID): GroceryItem {
		val item = groceryItemRepository.findByGroceryItemIdAndIsDeletedFalse(groceryItemId)
			?: throw IeumException(GroceryErrorCode.GROCERY_ITEM_NOT_FOUND)

		val accessible = when (item.visibility) {
			Visibility.FAMILY -> familyClient.isMember(item.familyId, me)
			Visibility.PRIVATE -> item.creatorAccountId == me
		}
		if (!accessible) {
			throw IeumException(GroceryErrorCode.GROCERY_ITEM_NOT_FOUND)
		}

		return item
	}

	private fun GroceryItem.toResponse() = GroceryItemResponse(
		groceryItemId = requireNotNull(groceryItemId),
		storageId = storageId,
		familyId = familyId,
		creatorAccountId = creatorAccountId,
		name = name,
		category = category,
		visibility = visibility,
		quantity = quantity,
		purchasePrice = purchasePrice,
		purchaseDate = purchaseDate,
		expiryDate = expiryDate,
	)
}
