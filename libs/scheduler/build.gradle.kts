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
	api("net.javacrumbs.shedlock:shedlock-spring:7.10.1")
	implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.10.1")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}

allOpen {
	annotation("jakarta.persistence.Entity")
}
