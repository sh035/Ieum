package com.ieum.grocery.service

import com.ieum.grocery.domain.enums.Visibility
import com.ieum.grocery.repository.GroceryItemRepository
import com.ieum.grpc.client.family.FamilyClient
import com.ieum.grpc.client.family.getMembersOf
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

// 그날 데일리 브리핑에 들어갈 grocery 몫을 계정별 개수로 센다.
@Service
class BriefingCountService(
	private val groceryItemRepository: GroceryItemRepository,
	private val familyClient: FamilyClient,
) {

	// 유통기한이 다음 날까지인 재고를 FAMILY 면 구성원 전체, PRIVATE 면 만든 사람 몫으로 센다.
	// 떠난 사람의 몫은 세지 않는다. family 조회가 실패하면 일부만 센 숫자를 주지 않도록 그대로 실패시킨다.
	@Transactional(readOnly = true)
	fun expiringCountsOn(date: LocalDate): Map<UUID, Int> {
		val items = groceryItemRepository.findAllByExpiryDateLessThanEqualAndIsDeletedFalse(date.plusDays(1))
		val members = familyClient.getMembersOf(items.map { it.familyId })

		val counts = mutableMapOf<UUID, Int>()
		items.forEach { item ->
			members[item.familyId].orEmpty()
				.filter { item.visibility == Visibility.FAMILY || it == item.creatorAccountId }
				.forEach { counts.merge(it, 1, Int::plus) }
		}
		return counts
	}
}
