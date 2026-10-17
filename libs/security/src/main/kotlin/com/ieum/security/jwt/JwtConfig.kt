package com.ieum.security.jwt

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class JwtConfig {

	@Bean
	fun jwtDecoder(properties: JwtProperties): JwtDecoder =
		NimbusJwtDecoder.withPublicKey(properties.rsaPublicKey()).build()
}
