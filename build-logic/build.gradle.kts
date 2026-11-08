plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("manifestGuard") {
            id = "imageorganiser.manifest-guard"
            implementationClass = "ManifestGuardPlugin"
        }
    }
}
