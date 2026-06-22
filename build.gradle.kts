plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.nmcp)
    `maven-publish`
    signing
}

group = "io.github.texport"
version = "1.0.1"

repositories {
    mavenLocal()
    mavenCentral()
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = true
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Javadoc>().configureEach {
    options {
        encoding = "UTF-8"
        if (this is StandardJavadocDocletOptions) {
            addStringOption("Xdoclint:none", "-quiet")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "ofd-network-client"
            
            pom {
                name.set("ofd-network-client")
                description.set("Lightweight Kotlin/JVM TCP network client for OFD KazakhTelecom communication")
                url.set("https://github.com/texport/ofd-network-client")
                
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                
                developers {
                    developer {
                        id.set("texport")
                        name.set("Sergey Ivanov")
                        email.set("ivanov.sergey.ekb@gmail.com")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/texport/ofd-network-client.git")
                    developerConnection.set("scm:git:ssh://github.com:texport/ofd-network-client.git")
                    url.set("https://github.com/texport/ofd-network-client")
                }
            }
        }
    }
}

signing {
    isRequired = false
    sign(publishing.publications["mavenJava"])
}

nmcp {
    publishAllPublicationsToCentralPortal {
        username.set(project.findProperty("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME"))
        password.set(project.findProperty("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD"))
        publishingType.set("USER_MANAGED")
    }
}

