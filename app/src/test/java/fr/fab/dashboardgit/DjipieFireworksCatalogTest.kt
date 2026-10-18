package fr.fab.dashboardgit

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Isolated fake records: validates the real Djipie naming and release scheme. */
class DjipieFireworksCatalogTest {
    private fun item(id:String, name:String, category:String, ext:String, assetId:Long):JSONObject {
        val file=JSONObject()
            .put("statut","disponible_prive")
            .put("nom_fichier",name)
            .put("release_tag","Fireworksetgitelec")
            .put("asset_id",assetId)
            .put("sha256","a".repeat(64))
            .put("taille_octets",27422L)
            .put("url","https://github.com/greenpower2669/Djipie/releases/download/Fireworksetgitelec/"+name)
        return JSONObject()
            .put("id",id).put("nom_original",name).put("categorie",category)
            .put("source","Fab (Release Git elec et fireworks)")
            .put("format",ext)
            .put("archive",if(ext=="wav") name else "25-CC0-bang-sfx.zip")
            .put("chemin_archive",name)
            .put("fichier_direct",file)
    }

    @Test fun realFireworksAndGuitarNamingIsDiscoverable() {
        val arr=JSONArray()
            .put(item("FAB-GUITAR-01","Musique_electrique_guitare_jingle_001.wav","MUSIQUES","wav",653486405))
            .put(item("FAB-FIREWORK-FW-LOOP","fw_loop.ogg","FX_EXPLOSIONS","ogg",653686891))
            .put(item("FAB-FIREWORK-BANG-01","bang_01.ogg","FX_EXPLOSIONS","ogg",653685522))
            .put(item("FAB-FIREWORK-SHOT-01","shot_01.ogg","FX_IMPACTS","ogg",653686954))
        val parsed=DjipieAudioCatalog.parse(arr.toString())
        assertEquals(4,parsed.size)
        assertEquals("Musique_electrique_guitare_jingle_001.wav",
            DjipieAudioCatalog.filter(parsed,"MUSIQUES","guitare").single().name)
        assertEquals("fw_loop.ogg",
            DjipieAudioCatalog.filter(parsed,"FX_EXPLOSIONS","fw_loop").single().name)
        assertEquals(1,DjipieAudioCatalog.filter(parsed,"FX_EXPLOSIONS","bang_01").size)
        assertEquals(1,DjipieAudioCatalog.filter(parsed,"FX_IMPACTS","shot").size)
    }
    @Test fun invalidShaAndDuplicatedIdCannotAppear() {
        val one=item("FAB-FIREWORK-BANG-01","bang_01.ogg","FX_EXPLOSIONS","ogg",653685522)
        val dup=item("FAB-FIREWORK-BANG-01","bang_02.ogg","FX_EXPLOSIONS","ogg",653685575)
        val invalid=item("FAB-FIREWORK-FW-03","fw_03.ogg","FX_EXPLOSIONS","ogg",653686533)
        invalid.getJSONObject("fichier_direct").put("sha256","not-a-sha")
        assertEquals(1,DjipieAudioCatalog.parse(JSONArray().put(one).put(dup).put(invalid).toString()).size)
    }
}
