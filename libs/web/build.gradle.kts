plugins {
	kotlin("plugin.jpa")
	kotlin("plugin.spring")
	id("io.spring.dependency-management")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
	}
}

dependencies {
	api("jakarta.persistence:jakarta.persistence-api")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	// 엔티티의 만든 / 고친 계정을 요청 토큰에서 읽는다. 엔티티가 있는 서비스는 모두 libs/security 로 이 의존성을 갖는다.
	compileOnly("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}
