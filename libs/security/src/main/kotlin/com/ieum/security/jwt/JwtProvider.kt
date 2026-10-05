package com.ieum.security.jwt

import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class JwtProvider(
	private val jwtEncoder: JwtEncoder,
	private val properties: JwtProperties,
) {

	fun createAccessToken(accountId: String): String {
		val now = Instant.now()

		val claims = JwtClaimsSet.builder()
			.subject(accountId)
			.issuedAt(now)
			.expiresAt(now.plus(properties.accessTokenTtl))
			.build()

		return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
	}
}
