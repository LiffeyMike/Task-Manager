plugins {
    id("io.github.liffeymike.java-conventions")
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.liffeymike"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation(project(":common"))

    // Runtime
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.graphql:spring-graphql-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework:spring-webflux")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
