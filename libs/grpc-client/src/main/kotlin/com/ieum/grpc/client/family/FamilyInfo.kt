package com.ieum.grpc.client.family

import java.util.UUID

data class FamilyInfo(
	val name: String,
	val members: Map<UUID, FamilyRole>,
)
