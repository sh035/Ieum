package com.ieum.grpc.client.planner

import java.time.LocalDate
import java.util.UUID

interface PlannerClient {

	// 그날 브리핑에 들어갈 계정별 개수. 셋 다 0 인 계정은 담지 않는다.
	fun getBriefingCounts(date: LocalDate): Map<UUID, PlannerBriefingCounts>
}
