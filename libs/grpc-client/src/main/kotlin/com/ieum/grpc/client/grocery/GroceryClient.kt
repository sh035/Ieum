package com.ieum.grpc.client.grocery

import java.time.LocalDate
import java.util.UUID

interface GroceryClient {

	// 그날 브리핑에 들어갈 계정별 유통기한 임박 재고 개수. 0 인 계정은 담지 않는다.
	fun getExpiringCounts(date: LocalDate): Map<UUID, Int>
}
