plugins {
	kotlin("jvm") version "2.3.21" apply false
	kotlin("plugin.spring") version "2.3.21" apply false
	kotlin("plugin.jpa") version "2.3.21" apply false
	id("org.springframework.boot") version "4.1.1" apply false
	id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
	group = "com.ieum"
	version = "0.0.1-SNAPSHOT"

	repositories {
		mavenCentral()
	}

	apply(plugin = "org.jetbrains.kotlin.jvm")

	if (parent?.name == "services") {
		apply(plugin = "org.jetbrains.kotlin.plugin.spring")
		apply(plugin = "org.springframework.boot")
		apply(plugin = "io.spring.dependency-management")
	}

	plugins.withId("org.jetbrains.kotlin.jvm") {
		extensions.configure<JavaPluginExtension> {
			toolchain.languageVersion.set(JavaLanguageVersion.of(25))
		}
		extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
			compilerOptions.freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
		}
		tasks.withType<Test> {
			useJUnitPlatform()
		}
	}

	plugins.withId("org.springframework.boot") {
		dependencies {
			"implementation"("org.springframework.boot:spring-boot-starter-webmvc")
			"implementation"("org.springframework.boot:spring-boot-starter-actuator")
			"implementation"("org.jetbrains.kotlin:kotlin-reflect")
			"implementation"("tools.jackson.module:jackson-module-kotlin")
			"testImplementation"("org.springframework.boot:spring-boot-starter-webmvc-test")
			"testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
			"testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
		}
	}
}
