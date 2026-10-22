package com.ieum.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
	val publicKey: String,
) {
	// 설정에 PEM 헤더 없이 base64 본문만 두기 위해 Spring 자동 변환 대신 직접 디코딩한다.
	fun rsaPublicKey(): RSAPublicKey =
		KeyFactory.getInstance("RSA")
			.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey))) as RSAPublicKey
}
