package io.github.tomuius.cartilou.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Le client Supabase : l'objet qui envoie les requêtes au serveur partagé
 * avec Canapilou. Il garde aussi la session sur le téléphone (vous restez
 * connecté d'un lancement à l'autre).
 *
 * L'adresse et la clé publique sont les mêmes que celles du site Canapilou
 * (suivi-series/js/config.js). Elles sont FAITES pour être visibles (dans
 * l'appli et dans le dépôt public) : la sécurité repose sur les règles de la
 * base (chacun ne voit que ses propres lignes), pas sur leur secret.
 *
 * NE JAMAIS mettre ici une clé qui commence par « sb_secret_ », ni la clé
 * « service_role » : elles contournent les règles de sécurité.
 */
object Supabase {

    private const val URL = "https://lrmcbzvnafwwefiublrl.supabase.co"
    private const val PUBLIC_KEY = "sb_publishable_lv9aMO5MTnOqsvAHLWfy1g_V6XtJFoM"

    val client: SupabaseClient = createSupabaseClient(supabaseUrl = URL, supabaseKey = PUBLIC_KEY) {
        // Connexion (e-mail et mot de passe, session gardée sur le téléphone).
        install(Auth)
        // Lecture et écriture des tables. Par défaut dans le schéma « cartilou »
        // (celui de nos tables), et non « public » (celui de Canapilou).
        install(Postgrest) {
            defaultSchema = "cartilou"
        }
    }
}
