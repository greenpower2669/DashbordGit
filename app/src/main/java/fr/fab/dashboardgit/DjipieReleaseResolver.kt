package fr.fab.dashboardgit

import org.json.JSONObject
import java.io.IOException

/**
 * Djipie Releases are recreated independently of the catalogue. A catalogue
 * asset_id is therefore a hint, not a permanent identifier: resolve by exact
 * release tag + exact asset filename, then compare size and SHA-256.
 *
 * This class is pure JVM code to keep the private download logic testable.
 */
object DjipieReleaseResolver {
    fun resolve(entry:DjipieAudioEntry, json:String):Long {
        val release=JSONObject(json)
        if(release.optString("tag_name")!=entry.releaseTag)
            throw IOException("La Release a changé : recharge le catalogue Djipie.")
        val assets=release.optJSONArray("assets")
            ?: throw IOException("GitHub ne renvoie pas la liste des sons de cette Release.")
        for(i in 0 until assets.length()) {
            val candidate=assets.optJSONObject(i) ?: continue
            if(candidate.optString("name")!=entry.assetName) continue
            if(candidate.optString("state","uploaded")!="uploaded")
                throw IOException("Le son est encore en cours de publication GitHub.")
            if(candidate.optLong("size",-1L)!=entry.sizeBytes)
                throw IOException("La taille du son a changé : recharge le catalogue.")
            val gitDigest=candidate.optString("digest")
            if(gitDigest.isNotBlank() && gitDigest!="sha256:"+entry.sha256)
                throw IOException("Le son a changé (SHA-256). Téléchargement refusé.")
            val currentId=candidate.optLong("id",-1L)
            if(currentId<=0L) throw IOException("Identifiant actuel du son indisponible.")
            return currentId
        }
        throw IOException(
            "Son absent de la Release actuelle : "+entry.assetName+
                ". Ce lien du catalogue est périmé, pas forcément ton jeton."
        )
    }
}
