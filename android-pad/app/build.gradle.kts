plugins { id("com.android.application") }
android {
    namespace = "dev.paul.dancepad"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.paul.dancepad"
        minSdk = 30
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
    }
    sourceSets["main"].java.setSrcDirs(listOf("src/main/java-app"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
