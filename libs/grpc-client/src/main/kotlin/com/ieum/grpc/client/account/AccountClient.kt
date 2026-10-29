package com.ieum.grpc.client.account

import java.util.UUID

interface AccountClient {
	// 없거나 탈퇴한 계정은 결과에서 빠진다.
	fun getNicknames(accountIds: Collection<UUID>): Map<UUID, String>
}
