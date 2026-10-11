package fr.fab.dashboardgit

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exercises the ACTUAL public REST data downloaded in CI for known red/green runs. */
class GithubWorkflowStatusRealApiTest {
    @Test fun officialGithubRunPayloadsClassifyWithoutGuessing() {
        val root=File("src/test/resources/github_live")
        val manifest=JSONArray(File(root,"manifest.json").readText())
        val seen=linkedSetOf<String>()
        for(i in 0 until manifest.length()) {
            val info=manifest.getJSONObject(i)
            val expected=info.getString("expectedConclusion")
            val id=info.getLong("runId")
            val sample=File(root,"api-$expected-$id.json")
            assertTrue("Missing independently verified API sample "+sample.name,sample.isFile)
            val json=JSONObject(sample.readText())
            assertEquals(id,json.getLong("id"))
            assertEquals("completed",json.getString("status"))
            assertEquals(expected,json.getString("conclusion"))
            val result=GithubWorkflowStatus.classify(json)
            assertTrue("Actual GitHub REST status was not classified",result.verified)
            assertEquals(if(expected=="success") "🟢" else "🔴",result.marker)
            seen.add(expected)
            println("REAL_API "+id+" -> "+result.marker+" "+result.label)
        }
        assertEquals(setOf("success","failure"),seen)
    }

    @Test fun staleSuccessNeverTurnsNewUnknownGreen() {
        val older=JSONObject("""{"id":20,"status":"completed","conclusion":"success"}""")
        val newer=JSONObject("""{"id":21,"status":"unknown","conclusion":""}""")
        assertEquals("🟢",GithubWorkflowStatus.classify(older).marker)
        assertFalse(GithubWorkflowStatus.classify(newer).verified)
        assertEquals("⚪",GithubWorkflowStatus.classify(newer).marker)
    }

    @Test fun inProgressAndQueueHaveRealSemantics() {
        val running=JSONObject("""{"id":1,"status":"in_progress","conclusion":null}""")
        val queued=JSONObject("""{"id":2,"status":"queued","conclusion":null}""")
        val empty=JSONObject("""{"id":3,"status":"completed","conclusion":null}""")
        assertEquals("🟡",GithubWorkflowStatus.classify(running).marker)
        assertEquals("🟠",GithubWorkflowStatus.classify(queued).marker)
        assertFalse(GithubWorkflowStatus.classify(empty).verified)
    }
}
