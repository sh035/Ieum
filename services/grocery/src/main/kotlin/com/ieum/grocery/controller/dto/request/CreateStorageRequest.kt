package com.ieum.grocery.controller.dto.request

import jakarta.validation.constraints.Size
import java.util.UUID

// 이름 하나하나의 검증은 서비스에서 한다. 목록 원소에 붙인 제약은 Kotlin이 바이트코드에 남기지 않는다.
data class CreateStorageRequest(
	val familyId: UUID,
	@field:Size(min = 1, max = 10)
	val names: List<String>,
)
