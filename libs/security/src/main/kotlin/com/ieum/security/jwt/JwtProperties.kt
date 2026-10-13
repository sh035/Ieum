package com.ieum.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.security.interfaces.RSAPublicKey

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
	val publicKey: RSAPublicKey,
)
