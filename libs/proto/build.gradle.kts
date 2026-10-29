plugins {
	id("io.spring.dependency-management")
	id("com.google.protobuf") version "0.10.0"
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
	}
}

dependencies {
	api("io.grpc:grpc-protobuf")
	api("io.grpc:grpc-stub")
	api("com.google.protobuf:protobuf-java")
}

// protoc 와 플러그인은 BOM 이 관리하지 않아서 Boot 4.1.1 이 쓰는 protobuf / grpc-java 버전에 맞춘다.
protobuf {
	protoc {
		artifact = "com.google.protobuf:protoc:4.35.1"
	}
	plugins {
		create("grpc") {
			artifact = "io.grpc:protoc-gen-grpc-java:1.83.1"
		}
	}
	generateProtoTasks {
		all().forEach { task ->
			task.plugins {
				create("grpc")
			}
		}
	}
}
