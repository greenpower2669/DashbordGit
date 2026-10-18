package fr.fab.dashboardgit

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class DjipieAudioCatalogTest {
    private fun entry(
        name:String="laser boom.wav",
        category:String="FX_EXPLOSIONS",
        url:String="https://github.com/greenpower2669/Djipie/releases/download/djipie-audio-prive-fx-explosions/SON-123__laser-boom.wav"
    ):JSONObject = JSONObject().apply {
        put("id","SON-123")
        put("nom_original",name)
        put("source","Sonniss")
        put("categorie",category)
        put("format","wav")
        put("duree_secondes",JSONObject.NULL)
        put("archive","GDC-2026.zip")
        put("chemin_archive","Pack/laser boom.wav")
        put("intensite","analyse nécessaire")
        put("projets_possibles","SpaceFortress")
        put("fichier_direct",JSONObject().apply {
            put("statut","disponible_prive")
            put("url",url)
            put("nom_fichier","SON-123__laser-boom.wav")
            put("release_tag","djipie-audio-prive-fx-explosions")
            put("asset_id",650634528L)
            put("sha256","a".repeat(64))
            put("taille_octets",123456L)
        })
    }

    @Test fun parsesVerifiedPrivateEntry() {
        val files=DjipieAudioCatalog.parse(JSONArray().put(entry()).toString())
        assertEquals(1,files.size)
        assertEquals("laser boom.wav",files.first().name)
        assertEquals(650634528L,files.first().assetId)
        assertNull(files.first().seconds)
        assertEquals("analyse nécessaire",files.first().intensity)
    }

    @Test fun blocksUnexpectedHostsAndPaths() {
        val attack=entry(url="https://untrusted.example.invalid/file.wav")
        val files=DjipieAudioCatalog.parse(JSONArray().put(attack).toString())
        assertTrue(files.isEmpty())
        val mismatch=entry(url="https://github.com/greenpower2669/Djipie/releases/download/fake/file.wav")
        assertTrue(DjipieAudioCatalog.parse(JSONArray().put(mismatch).toString()).isEmpty())
    }

    @Test fun filtersCategoriesAccentsAndPaths() {
        val one=DjipieAudioCatalog.parse(JSONArray().put(entry(name="Éclat spatial.wav")).toString())
        assertEquals(1,DjipieAudioCatalog.filter(one,"TOUS","eclat").size)
        assertEquals(1,DjipieAudioCatalog.filter(one,"FX_EXPLOSIONS","Pack").size)
        assertEquals(0,DjipieAudioCatalog.filter(one,"ANIMAUX","").size)
    }

    @Test fun rejectsMissingHashAndDuplicateId() {
        val bad=entry()
        bad.getJSONObject("fichier_direct").put("sha256","bad")
        assertTrue(DjipieAudioCatalog.parse(JSONArray().put(bad).toString()).isEmpty())
        val dup=entry()
        val two=DjipieAudioCatalog.parse(JSONArray().put(dup).put(entry()).toString())
        assertEquals(1,two.size)
    }

    @Test fun formatsDisplaySafely() {
        assertEquals("Durée à analyser",DjipieAudioCatalog.durationText(null))
        assertTrue(DjipieAudioCatalog.sizeText(1048576L).contains("Mio"))
        assertEquals(emptyList<DjipieAudioEntry>(),DjipieAudioCatalog.parse("[]"))
    }
}
