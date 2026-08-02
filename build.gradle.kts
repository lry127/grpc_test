import com.google.protobuf.gradle.*

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.6"
}

group = "us.leaf3stones"
version = "0.0.1-SNAPSHOT"
description = "grpc-test"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    val jjwtVersion= "0.12.6"
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    implementation("org.springframework.boot:spring-boot-starter-grpc-server")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.apache.logging.log4j:log4j-api")
    implementation("org.apache.logging.log4j:log4j-core")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("io.grpc:grpc-kotlin-stub")
    implementation("com.google.protobuf:protobuf-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-security")

    testImplementation("org.springframework.boot:spring-boot-starter-grpc-server-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${dependencyManagement.importedProperties["protobuf-java.version"]}" // Use your version
    }

    plugins {
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${dependencyManagement.importedProperties["grpc-kotlin.version"]}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpckt")
            }
            task.builtins {
                id("kotlin")
            }
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}