dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../mzio-mzmine/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "mzmine3"