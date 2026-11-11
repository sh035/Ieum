package com.ieum.account.controller.dto.response

// 비밀번호를 바꾸면 모든 기기 토큰이 끊기므로 지금 기기용 새 토큰을 같이 준다. 안 바꿨으면 null 이다.
data class UpdateAccountResponse(
	val account: AccountResponse,
	val token: TokenResponse?,
)
