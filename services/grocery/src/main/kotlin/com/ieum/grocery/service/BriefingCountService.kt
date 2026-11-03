package com.ieum.grocery.service

import com.ieum.grocery.domain.enums.Visibility
import com.ieum.grocery.repository.GroceryItemRepository
import com.ieum.grpc.client.family.FamilyClient
import org.slf4j.LoggerFactory
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

	private val log = LoggerFactory.getLogger(javaClass)

	// 유통기한이 다음 날까지인 재고를 FAMILY 면 구성원 전체, PRIVATE 면 만든 사람 몫으로 센다.
	// family 조회가 실패하면 일부만 센 숫자를 주지 않도록 그대로 실패시킨다.
	// 가족마다 family 를 한 번씩 부른다. 가족이 많아져 notification 의 deadline 을 넘기면 family 에 일괄 조회를 추가한다.
	@Transactional(readOnly = true)
	fun expiringCountsOn(date: LocalDate): Map<UUID, Int> {
		val items = groceryItemRepository.findAllByExpiryDateLessThanEqualAndIsDeletedFalse(date.plusDays(1))
		val members = items.filter { it.visibility == Visibility.FAMILY }
			.map { it.familyId }
			.distinct()
			.associateWith { familyMembers(it) }

		val counts = mutableMapOf<UUID, Int>()
		items.forEach { item ->
			val accountIds = when (item.visibility) {
				Visibility.FAMILY -> members[item.familyId].orEmpty()
				Visibility.PRIVATE -> setOf(item.creatorAccountId)
			}
			accountIds.forEach { counts.merge(it, 1, Int::plus) }
		}
		return counts
	}

	private fun familyMembers(familyId: UUID): Set<UUID> =
		familyClient.getFamily(familyId)?.members?.keys
			?: emptySet<UUID>().also { log.warn("Briefing family not found. familyId={}", familyId) }
}
