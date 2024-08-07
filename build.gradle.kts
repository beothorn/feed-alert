plugins {
    kotlin("jvm") version "1.9.23"
    application
}

group = "br.com.isageek"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.apptasticsoftware:rssreader:3.7.0")
    implementation("net.harawata:appdirs:1.2.2")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    // Including dependencies
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}