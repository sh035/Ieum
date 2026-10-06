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
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}
