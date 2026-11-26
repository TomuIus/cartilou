// Fichier de construction principal : réglages communs à tous les modules.

// Version de Kotlin : le plugin Android 9 compile le Kotlin lui-même
// (« built-in Kotlin ») avec Kotlin 2.2.10 par défaut. Pour une version plus
// récente, on ajoute ici le plugin Kotlin avec la version voulue (même méthode
// que Canapilou, d'après les notes de version d'AGP 9.0). Elle doit être
// IDENTIQUE à « kotlin » dans gradle/libs.versions.toml.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
