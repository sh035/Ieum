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
	api(project(":libs:proto"))
	implementation(project(":libs:web"))
	implementation("org.springframework:spring-web")
	api("org.springframework.boot:spring-boot-starter-grpc-client")

	testImplementation("io.grpc:grpc-inprocess")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
