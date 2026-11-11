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

	// 비밀번호 변경 시 한 번에 끊을 수 있게 계정별로 토큰 해시를 모아 둔다.
	fun issue(accountId: String): String {
		val token = UUID.randomUUID().toString()
		val hash = hash(token)
		redisTemplate.opsForValue().set(TOKEN_PREFIX + hash, accountId, refreshTokenTtl)
		redisTemplate.opsForSet().add(ACCOUNT_PREFIX + accountId, hash)
		redisTemplate.expire(ACCOUNT_PREFIX + accountId, refreshTokenTtl)

		return token
	}

	fun findAccountId(token: String): String? =
		redisTemplate.opsForValue().get(TOKEN_PREFIX + hash(token))

	fun revoke(token: String) {
		val hash = hash(token)
		val accountId = redisTemplate.opsForValue().getAndDelete(TOKEN_PREFIX + hash) ?: return
		redisTemplate.opsForSet().remove(ACCOUNT_PREFIX + accountId, hash)
	}

	fun revokeAll(accountId: String) {
		// Set 을 통째로 지우면 그 사이 새로 발급된 토큰까지 목록에서 빠지므로 읽은 것만 뺀다.
		val hashes = redisTemplate.opsForSet().members(ACCOUNT_PREFIX + accountId).orEmpty()
		if (hashes.isEmpty()) return
		redisTemplate.delete(hashes.map { TOKEN_PREFIX + it })
		redisTemplate.opsForSet().remove(ACCOUNT_PREFIX + accountId, *hashes.toTypedArray())
	}

	private fun hash(token: String): String =
		HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

	companion object {
		private const val TOKEN_PREFIX = "refresh:"
		private const val ACCOUNT_PREFIX = "refresh-account:"
	}
}
