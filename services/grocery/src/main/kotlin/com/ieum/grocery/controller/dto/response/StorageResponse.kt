package com.ieum.grocery.controller.dto.response

import java.util.UUID

data class StorageResponse(
	val storageId: UUID,
	val familyId: UUID,
	val name: String,
)
