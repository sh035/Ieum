package com.ieum.grocery.service

import com.ieum.grocery.domain.enums.Visibility
import com.ieum.grocery.repository.GroceryItemRepository
import com.ieum.grocery.repository.StorageRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

// family 의 outbox 이벤트는 두 번 올 수 있어서 다시 처리해도 결과가 같게 만든다.
@Service
class FamilyEventService(
	private val groceryItemRepository: GroceryItemRepository,
	private val storageRepository: StorageRepository,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	// 냉장고에 물건이 남아 있으니 떠난 사람의 개인 재고를 가족이 볼 수 있게 연다.
	// 다시 들어와 새로 만든 개인 재고가 늦게 온 같은 이벤트로 열리지 않게, 떠난 시각 전에 만든 것만 바꾼다.
	@Transactional
	fun openPrivateItems(familyId: UUID, accountId: UUID, leftAt: LocalDateTime) {
		val items = groceryItemRepository.findAllByFamilyIdAndVisibilityAndCreatorAccountIdAndIsDeletedFalse(familyId, Visibility.PRIVATE, accountId)
			.filterNot { it.createdAt.isAfter(leftAt) }
		items.forEach { it.visibility = Visibility.FAMILY }

		log.debug("Private grocery items opened. familyId={}, accountId={}, count={}", familyId, accountId, items.size)
	}

	@Transactional
	fun deleteFamily(familyId: UUID) {
		groceryItemRepository.findAllByFamilyIdAndIsDeletedFalse(familyId).forEach { it.delete() }
		storageRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId).forEach { it.delete() }

		log.debug("Grocery family data deleted. familyId={}", familyId)
	}
}
