plugins {
	kotlin("plugin.spring")
	id("io.spring.dependency-management")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
	}
}

dependencies {
	implementation(project(":libs:web"))
	implementation("org.springframework:spring-web")
	api("org.springframework.boot:spring-boot-starter-grpc-server")

	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
