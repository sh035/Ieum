plugins {
	kotlin("plugin.jpa")
}

dependencies {
	implementation(project(":libs:web"))
	implementation(project(":libs:security"))
	implementation(project(":libs:proto"))
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-grpc-server")
	runtimeOnly("org.postgresql:postgresql")

	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.springframework.boot:spring-boot-starter-grpc-server-test")
	testImplementation("org.springframework.boot:spring-boot-starter-grpc-client-test")
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}
