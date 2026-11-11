package com.ieum.family.service

import com.ieum.family.exception.FamilyErrorCode
import com.ieum.grpc.client.account.AccountClient
import com.ieum.web.exception.IeumException
import java.util.UUID

// 가족에 들어올 때 이 가족 닉네임의 첫 값으로 쓴다.
internal fun AccountClient.accountNickname(accountId: UUID): String =
	getNicknames(setOf(accountId))[accountId] ?: throw IeumException(FamilyErrorCode.ACCOUNT_NOT_FOUND)
