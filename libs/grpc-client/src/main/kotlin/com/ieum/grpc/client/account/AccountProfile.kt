package com.ieum.grpc.client.account

import java.util.UUID

data class AccountProfile(
	val accountId: UUID,
	val nickname: String,
)
