package com.ieum.grocery.service

import com.ieum.grocery.controller.dto.request.CreateStorageRequest
import com.ieum.grocery.controller.dto.request.UpdateStorageRequest
import com.ieum.grocery.controller.dto.response.StorageResponse
import com.ieum.grocery.domain.entity.Storage
import com.ieum.grocery.exception.GroceryErrorCode
import com.ieum.grocery.repository.GroceryItemRepository
import com.ieum.grocery.repository.StorageRepository
import com.ieum.web.exception.IeumException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class StorageService(
	private val storageRepository: StorageRepository,
	private val groceryItemRepository: GroceryItemRepository,
	private val familyClient: FamilyClient,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// 처음 쓸 때 여러 장소를 한 번에 만든다. 요청 안의 중복 이름은 하나로 합친다.
	@Transactional
	fun createStorages(accountId: String, request: CreateStorageRequest): List<StorageResponse> {
		checkMember(request.familyId, UUID.fromString(accountId))

		val names = request.names.map { it.trim() }.distinct()
		if (names.any { it.isEmpty() || it.length > MAX_NAME_LENGTH }) {
			throw IeumException(GroceryErrorCode.INVALID_STORAGE_NAME)
		}
		val existing = storageRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(request.familyId).map { it.name }
		if (names.any { it in existing }) {
			throw IeumException(GroceryErrorCode.STORAGE_NAME_DUPLICATED)
		}

		val storages = saveCheckingDuplicate {
			storageRepository.saveAllAndFlush(names.map { Storage(familyId = request.familyId, name = it) })
		}

		log.debug("Storages created. familyId={}, count={}", request.familyId, storages.size)

		return storages.map { it.toResponse() }
	}

	fun getStorages(accountId: String, familyId: UUID): List<StorageResponse> {
		checkMember(familyId, UUID.fromString(accountId))

		return storageRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId).map { it.toResponse() }
	}

	fun updateStorage(accountId: String, storageId: UUID, request: UpdateStorageRequest): StorageResponse {
		val storage = findAccessibleStorage(UUID.fromString(accountId), storageId)
		storage.name = request.name.trim()

		return saveCheckingDuplicate { storageRepository.saveAndFlush(storage) }.toResponse()
	}

	// 남은 물건이 함께 사라지지 않도록 비어 있는 장소만 지운다.
	fun deleteStorage(accountId: String, storageId: UUID) {
		val storage = findAccessibleStorage(UUID.fromString(accountId), storageId)
		if (groceryItemRepository.existsByStorageIdAndIsDeletedFalse(storageId)) {
			throw IeumException(GroceryErrorCode.STORAGE_NOT_EMPTY)
		}

		storage.delete()
		storageRepository.save(storage)
	}

	// 구성원이 아니면 장소의 존재 자체를 숨긴다.
	fun findAccessibleStorage(me: UUID, storageId: UUID): Storage {
		val storage = storageRepository.findByStorageIdAndIsDeletedFalse(storageId)
			?: throw IeumException(GroceryErrorCode.STORAGE_NOT_FOUND)

		if (!familyClient.isMember(storage.familyId, me)) {
			throw IeumException(GroceryErrorCode.STORAGE_NOT_FOUND)
		}

		return storage
	}

	private fun checkMember(familyId: UUID, accountId: UUID) {
		if (!familyClient.isMember(familyId, accountId)) {
			throw IeumException(GroceryErrorCode.FAMILY_NOT_FOUND)
		}
	}

	// 동시에 같은 이름을 만들면 앞선 확인을 함께 통과하므로 unique index 위반으로 걸러낸다.
	private fun <T> saveCheckingDuplicate(save: () -> T): T =
		try {
			save()
		} catch (e: DataIntegrityViolationException) {
			throw IeumException(GroceryErrorCode.STORAGE_NAME_DUPLICATED)
		}

	private fun Storage.toResponse() = StorageResponse(
		storageId = requireNotNull(storageId),
		familyId = familyId,
		name = name,
	)

	companion object {
		private const val MAX_NAME_LENGTH = 50
	}
}
