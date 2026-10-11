package fr.fab.dashboardgit

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exact red, green and ongoing states captured from LIVE GitHub REST in CI. */
class GithubWorkflowStatusRealApiTest {
    @Test fun officialGithubRunPayloadsClassifyWithoutGuessing() {
        val root=File("src/test/resources/github_live")
        val manifest=JSONArray(File(root,"manifest.json").readText())
        val seen=linkedSetOf<String>()
        for(i in 0 until manifest.length()) {
            val info=manifest.getJSONObject(i)
            val category=info.getString("case")
            val expectedStatus=info.getString("expectedStatus")
            val expectedConclusion=info.optString("expectedConclusion","")
            val id=info.getLong("runId")
            val sample=File(root,"api-$category-$id.json")
            assertTrue("Missing real, independently verified GitHub API fixture "+sample.name,sample.isFile)
            val data=JSONObject(sample.readText())
            assertEquals(id,data.getLong("id"))
            assertEquals(expectedStatus,data.getString("status"))
            if(category!="running") assertEquals(expectedConclusion,data.getString("conclusion"))
            val result=GithubWorkflowStatus.classify(data)
            assertTrue("Actual GitHub REST status was not recognized",result.verified)
            val expectedMarker=when(category) {
                "success" -> "🟢"
                "failure" -> "🔴"
                "running" -> if(expectedStatus=="in_progress") "🟡" else "🟠"
                else -> throw AssertionError("Unknown live fixture category")
            }
            assertEquals("Incorrect real GitHub run color #$id",expectedMarker,result.marker)
            seen.add(category)
            println("REAL_API "+id+" -> "+result.marker+" "+result.label)
        }
        assertEquals(setOf("success","failure","running"),seen)
    }

    @Test fun olderSuccessCannotHideNewUnknown() {
        val older=JSONObject("""{"id":20,"status":"completed","conclusion":"success"}""")
        val newer=JSONObject("""{"id":21,"status":"unknown","conclusion":""}""")
        assertEquals("🟢",GithubWorkflowStatus.classify(older).marker)
        assertFalse(GithubWorkflowStatus.classify(newer).verified)
        assertEquals("⚪",GithubWorkflowStatus.classify(newer).marker)
    }

    @Test fun queuedAndIncompleteCompletionNeverLookGreen() {
        val queued=JSONObject("""{"id":2,"status":"queued","conclusion":null}""")
        val empty=JSONObject("""{"id":3,"status":"completed","conclusion":null}""")
        assertEquals("🟠",GithubWorkflowStatus.classify(queued).marker)
        assertFalse(GithubWorkflowStatus.classify(empty).verified)
        assertEquals("⚪",GithubWorkflowStatus.classify(empty).marker)
    }
}
