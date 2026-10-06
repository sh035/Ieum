package com.ieum.security.jwt

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class JwtProvider(
	private val jwtEncoder: JwtEncoder,
	@Value("\${jwt.access-token-ttl}") private val accessTokenTtl: Duration,
) {

	fun createAccessToken(accountId: String): String {
		val now = Instant.now()

		val claims = JwtClaimsSet.builder()
			.subject(accountId)
			.issuedAt(now)
			.expiresAt(now.plus(accessTokenTtl))
			.build()

		return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
	}
}
