plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.bundles.build.logic.plugins)
}

// Workaround: access version catalog in precompiled script plugins
// by providing plugin classpath dependencies as compileOnly
