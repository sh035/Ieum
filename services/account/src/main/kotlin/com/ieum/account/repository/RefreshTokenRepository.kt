package com.ieum.account.repository

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.UUID

@Repository
class RefreshTokenRepository(
	private val redisTemplate: StringRedisTemplate,
	@Value("\${jwt.refresh-token-ttl}") private val refreshTokenTtl: Duration,
) {

	fun issue(accountId: String): String {
		val token = UUID.randomUUID().toString()
		redisTemplate.opsForValue().set(key(token), accountId, refreshTokenTtl)

		return token
	}

	fun findAccountId(token: String): String? =
		redisTemplate.opsForValue().get(key(token))

	fun revoke(token: String) {
		redisTemplate.delete(key(token))
	}

	private fun key(token: String): String {
		val hash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())

		return "refresh:${HexFormat.of().formatHex(hash)}"
	}
}
