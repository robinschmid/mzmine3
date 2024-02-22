rootProject.name = "mzmine3"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../mzio-mzmine/gradle/libs.versions.toml"))
        }
    }
}

