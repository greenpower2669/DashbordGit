package fr.fab.dashboardgit

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class DjipieReleaseResolverTest {
    private fun entry(id:Long=650663951L)=DjipieAudioEntry(
        id="SON-8825158d8fb88488147716ca",
        name="25 Church Bells, Far Distance.wav",
        category="NON_CLASSES",source="Sonniss",format="wav",
        seconds=null,originalArchive="Sonniss3.zip",
        archivePath="Church/25 Church Bells.wav",
        intensity="analyse nécessaire",projects="",assetId=id,
        releaseTag="djipie-audio-prive-non-classes",
        assetName="SON-8825158d8fb88488147716ca__25-church-bells-far-distance-rural-soundscape-distant-hills-ridge.wav",
        sha256="49af5cedf7e5dea91efb4084f8d25cb01bbf4581bc1923e31bf5dc249a381b0f",
        sizeBytes=26100224L,
        releaseUrl="https://github.com/greenpower2669/Djipie/releases/download/djipie-audio-prive-non-classes/SON-8825158d8fb88488147716ca__25-church-bells-far-distance-rural-soundscape-distant-hills-ridge.wav"
    )

    private fun release(entry:DjipieAudioEntry, id:Long=652011955L, sha:String=entry.sha256,
                        name:String=entry.assetName, size:Long=entry.sizeBytes):String =
        JSONObject().put("tag_name",entry.releaseTag).put("assets",
            JSONArray().put(JSONObject().put("name",name)
                .put("id",id).put("size",size)
                .put("digest","sha256:"+sha).put("state","uploaded")
            )
        ).toString()

    @Test fun resolvesRepublishedAssetWithoutTrustingOldCatalogId() {
        val original=entry()
        assertNotEquals(original.assetId,652011955L)
        assertEquals(652011955L,DjipieReleaseResolver.resolve(original,release(original)))
    }

    @Test fun failsClosedWhenContentChanges() {
        val original=entry()
        assertThrows(IOException::class.java) {
            DjipieReleaseResolver.resolve(original,release(original,sha="b".repeat(64)))
        }
        assertThrows(IOException::class.java) {
            DjipieReleaseResolver.resolve(original,release(original,size=42L))
        }
    }

    @Test fun explainsMissingAssetInsteadOfAccusingToken() {
        val original=entry()
        val message=assertThrows(IOException::class.java) {
            DjipieReleaseResolver.resolve(original,release(original,name="un-autre-son.wav"))
        }.message.orEmpty()
        assertTrue(message.contains("périmé"))
        assertFalse(message.contains("invalide"))
    }

    @Test fun doesNotMixReleases() {
        val original=entry()
        val payload=JSONObject(release(original)).put("tag_name","autre-release").toString()
        assertThrows(IOException::class.java) {
            DjipieReleaseResolver.resolve(original,payload)
        }
    }
}
