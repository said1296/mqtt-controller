import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.5.0-SNAPSHOT"
    id("io.spring.dependency-management") version "1.0.10.RELEASE"
    kotlin("jvm") version "1.4.21"
    kotlin("plugin.spring") version "1.4.21"
    `maven-publish`
    signing
}

group = "com.github.said1296"
version = "1.8"

java {
    sourceCompatibility = JavaVersion.VERSION_15
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
    maven("https://invesdwin.de/repo/invesdwin-oss-remote/")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Aspect Oriented Programming (AOP)
    implementation("org.aspectj:aspectjweaver:1.9.6")
    implementation("org.springframework:spring-aop:5.3.2")
    implementation("de.invesdwin:invesdwin-instrument:1.0.9")

    // Serialization
    implementation("com.google.code.gson:gson:2.8.6")

    // MQTT
    implementation("org.springframework.integration:spring-integration-mqtt:5.4.2")
}

tasks.bootJar { enabled = false }
tasks.jar { enabled = true }

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "15"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = group as String?
            artifactId = "mqttcontroller"
            version = version
            from(components["java"])

            pom {
                name.set("Mqtt Controller for Spring Boot")
                description.set("Allows to easily suscribe, publish and deserialize payloads from an MQTT Broker")
                url.set("https://github.com/said1296/mqttcontroller")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("said1296")
                        name.set("Aarón Said García Chávez")
                        email.set("aaron.gavez@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git@github.com:said1296/mqtt-controller.git")
                    url.set("https://github.com/said1296/mqttcontroller")
                }
            }
        }
        repositories {
            maven {
                setUrl("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials {
                    username = project.property("user") as String
                    password = project.property("password") as String
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
