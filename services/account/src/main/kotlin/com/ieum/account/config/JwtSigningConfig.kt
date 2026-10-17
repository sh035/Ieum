package com.ieum.account.config

import com.ieum.security.jwt.JwtProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

// 토큰 발급자는 account 하나뿐이므로 서명 키는 libs/security가 아니라 이 서비스에서만 요구한다.
@Configuration
class JwtSigningConfig {

	@Bean
	fun jwtEncoder(
		properties: JwtProperties,
		@Value("\${jwt.private-key}") privateKey: String,
	): JwtEncoder {
		val rsaPrivateKey = KeyFactory.getInstance("RSA")
			.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey))) as RSAPrivateKey

		return NimbusJwtEncoder.withKeyPair(properties.rsaPublicKey(), rsaPrivateKey).build()
	}
}
