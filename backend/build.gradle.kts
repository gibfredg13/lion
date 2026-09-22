plugins {
    java
    alias(libs.plugins.error.prone)
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    errorprone(libs.error.prone)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.websocket)
    implementation(libs.spring.session.jdbc)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.security.crypto)
    implementation(libs.sentry.spring.boot.starter)
    implementation(libs.azure.ai.openai)
    implementation(libs.caffeine)
    runtimeOnly(libs.micrometer.registry.prometheus)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.hsqldb)
    testImplementation(libs.spring.boot.starter.test)
}

// Spring resolves @PathVariable/@RequestParam names by reflection when they are not spelled out.
// Without -parameters that lookup throws at request time, which is why PUT /api/admin/level/{level}
// /enabled always returned 500 - the admin level toggle had never actually worked.
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
}