package com.ieum.account.controller.dto.response

data class TokenResponse(
	val accessToken: String,
	val refreshToken: String,
)
