package fr.fab.dashboardgit

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Public-independent catalogue parser. No sound files or private credentials embedded. */
data class DjipieAudioEntry(
    val id:String,
    val name:String,
    val category:String,
    val source:String,
    val format:String,
    val seconds:Double?,
    val originalArchive:String,
    val archivePath:String,
    val intensity:String,
    val projects:String,
    val assetId:Long,
    val releaseTag:String,
    val assetName:String,
    val sha256:String,
    val sizeBytes:Long,
    val releaseUrl:String
)

object DjipieAudioCatalog {
    private const val PRIVATE_RELEASE_PREFIX =
        "https://github.com/greenpower2669/Djipie/releases/download/"
    private val hexSha = Regex("[a-f0-9]{64}")
    private val idPattern = Regex("[A-Za-z0-9_-]{2,100}")
    private val filePattern = Regex("[A-Za-z0-9_.-]{1,210}")

    fun parse(body:String):List<DjipieAudioEntry> {
        val array=JSONArray(body)
        val result=ArrayList<DjipieAudioEntry>(array.length())
        val ids=HashSet<String>()
        for(i in 0 until array.length()) {
            val obj=array.optJSONObject(i) ?: continue
            val direct=obj.optJSONObject("fichier_direct") ?: continue
            if(direct.optString("statut")!="disponible_prive") continue
            val id=obj.optString("id")
            val name=obj.optString("nom_original")
            val category=obj.optString("categorie")
            val format=obj.optString("format").lowercase(Locale.ROOT)
            val tag=direct.optString("release_tag")
            val assetName=direct.optString("nom_fichier")
            val sha=direct.optString("sha256").lowercase(Locale.ROOT)
            val assetId=direct.optLong("asset_id",-1)
            val size=direct.optLong("taille_octets",-1)
            val url=direct.optString("url")
            if(!idPattern.matches(id) || name.isBlank() || category.isBlank() ||
               format !in setOf("ogg","wav","mp3") || !filePattern.matches(tag) ||
               !filePattern.matches(assetName) || !hexSha.matches(sha) ||
               assetId<=0 || size<=0 || !url.startsWith(PRIVATE_RELEASE_PREFIX) ||
               url!=PRIVATE_RELEASE_PREFIX+tag+"/"+assetName || !ids.add(id)) continue
            result.add(DjipieAudioEntry(
                id=id, name=name, category=category,
                source=obj.optString("source"), format=format,
                seconds=if(obj.isNull("duree_secondes")) null else
                    obj.optDouble("duree_secondes",Double.NaN).takeIf { it.isFinite() && it>=0 },
                originalArchive=obj.optString("archive"),
                archivePath=obj.optString("chemin_archive"),
                intensity=obj.optString("intensite"),
                projects=obj.optString("projets_possibles"),
                assetId=assetId, releaseTag=tag, assetName=assetName, sha256=sha,
                sizeBytes=size, releaseUrl=url
            ))
        }
        return result
    }

    private fun normalized(text:String):String = Normalizer.normalize(
        text,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.FRANCE)

    fun filter(entries:List<DjipieAudioEntry>,category:String?,search:String):List<DjipieAudioEntry> {
        val needle=normalized(search.trim())
        return entries.filter { e ->
            (category.isNullOrBlank() || category=="TOUS" || e.category==category) &&
                (needle.isBlank() || normalized(e.name+" "+e.source+" "+e.category+" "+
                    e.archivePath+" "+e.projects+" "+e.originalArchive).contains(needle))
        }.sortedWith(compareBy<DjipieAudioEntry>({it.category},{it.name.lowercase(Locale.FRANCE)}))
    }

    fun sizeText(size:Long):String = when {
        size>=1024L*1024L -> String.format(Locale.FRANCE,"%.1f Mio",size/1048576.0)
        size>=1024L -> String.format(Locale.FRANCE,"%.0f Kio",size/1024.0)
        else -> "$size o"
    }
    fun durationText(seconds:Double?):String =
        if(seconds==null) "Durée à analyser"
        else String.format(Locale.FRANCE,"%.1f s",seconds)
}
