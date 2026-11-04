plugins {
	id("io.spring.dependency-management")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
	}
}

dependencies {
	api("org.springframework.boot:spring-boot-testcontainers")
	api("org.testcontainers:testcontainers-postgresql")
	api("org.testcontainers:testcontainers-junit-jupiter")
	api("org.springframework:spring-test")
	implementation("org.springframework.security:spring-security-oauth2-jose")
}
