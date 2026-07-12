plugins {
    `java-library`
    id("eclipse")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories {
    mavenCentral()
}

tasks.withType<Test> {
    useJUnitPlatform()
}
