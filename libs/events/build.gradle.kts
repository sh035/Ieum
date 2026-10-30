plugins {
	id("io.spring.dependency-management")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
	}
}

dependencies {
	api("tools.jackson.module:jackson-module-kotlin")

	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
