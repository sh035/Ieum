package com.ieum.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Duration

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
	val privateKey: RSAPrivateKey,
	val publicKey: RSAPublicKey,
	val accessTokenTtl: Duration,
	val refreshTokenTtl: Duration,
)
