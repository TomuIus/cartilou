package io.github.tomuius.cartilou

import android.app.Application
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex

/**
 * L'« application » au sens d'Android : créée une seule fois, avant tout
 * écran, à chaque lancement. On y prépare ce dont toute l'appli a besoin.
 *
 * Déclarée dans AndroidManifest.xml (android:name=".CartilouApplication").
 */
class CartilouApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Le client TCGdex range ses réponses dans le dossier cache de l'appli.
        Tcgdex.init(cacheDir)
    }
}
