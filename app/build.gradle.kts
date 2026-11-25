import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Clés Supabase lues dans local.properties (fichier ignoré par git), jamais écrites dans le code.
// Modèle à copier : local.properties.example
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String): String =
    (localProperties.getProperty(name) ?: System.getenv(name) ?: "").trim()

android {
    namespace = "com.example.lostfoundcampus"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.lostfoundcampus"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL").trimEnd('/')}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${secret("SUPABASE_PUBLISHABLE_KEY")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        // BuildConfig : clés Supabase et indicateur DEBUG (journal réseau).
        buildConfig = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    // Navigation Component : graphe des écrans et barre de navigation basse.
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    // ViewPager2 : galerie photos à défilement horizontal du détail d'annonce.
    implementation(libs.viewpager2)
    // osmdroid : cartes OpenStreetMap, sans clé d'API.
    implementation(libs.osmdroid)
    // Retrofit + Gson + OkHttp : appels à l'API REST de Supabase (Auth, PostgREST, Storage).
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    // Journal des requêtes réseau, utilisé seulement dans les builds debug.
    implementation(libs.okhttp.logging)
    // Glide : affichage et cache des photos (exigence du rapport).
    implementation(libs.glide)
    // ExifInterface : remettre droites les photos de l'appareil photo avant compression.
    implementation(libs.exifinterface)
    // Palette : couleur dominante d'une photo.
    implementation(libs.palette)
    // Position réelle pour « J'ai trouvé un objet » et « Ma position ».
    implementation(libs.play.services.location)
    // ML Kit (modèles intégrés, sans Firebase) : étiquettes et texte visible.
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.text.recognition)
    // MediaPipe : localisation et classement des objets (modèles .tflite dans assets).
    implementation(libs.mediapipe.tasks.vision)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
