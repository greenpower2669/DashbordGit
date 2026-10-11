package fr.fab.dashboardgit

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * REAL Github.com pages downloaded during this very same CI job.
 * Ground truth is obtained independently via the official REST API in CI.
 * UNKNOWN is acceptable; any wrong red/green/yellow is a test FAILURE.
 */
class GithubActionHtmlRealSiteTest {
    @Test fun realPublicGithubPagesMatchIndependentApiTruthOrStayUnknown() {
        val root=File("src/test/resources/github_live")
        val manifest=File(root,"manifest.json")
        assertTrue("Missing live GitHub page captures from CI",manifest.isFile)
        val cases=JSONArray(manifest.readText())
        assertTrue("Expected real success, failure and running cases",cases.length>=3)
        val categories=linkedSetOf<String>()
        var verified=0
        var unknown=0
        for(i in 0 until cases.length()) {
            val item=cases.getJSONObject(i)
            val category=item.getString("case")
            val expectedStatus=item.getString("expectedStatus")
            val expectedConclusion=item.optString("expectedConclusion","")
            val id=item.getLong("runId")
            val sample=File(root,"$category-$id.html")
            assertTrue("Missing real public GitHub page "+sample.name,sample.isFile)
            val html=sample.readText()
            assertTrue("Real GitHub page is unusually small",html.length>10_000)
            assertTrue("Real GitHub page is not associated with requested run",html.contains(id.toString()))
            categories.add(category)
            val rawState=GithubActionHtmlParser.fromRunPage(id,html)
            val state=if(rawState==null) null else JSONObject()
                .put("status",rawState.status).put("conclusion",rawState.conclusion)
            val classification=GithubWorkflowStatus.classify(state)
            if(!classification.verified) {
                unknown++
                println("REAL_HTML $category #$id -> UNKNOWN, authorization required")
            } else {
                verified++
                if(category=="running") {
                    assertTrue("False terminal result on an ongoing run!",
                        rawState!!.status in setOf("in_progress","queued","pending","waiting"))
                    assertEquals("Running build must have no conclusion","",rawState.conclusion)
                } else {
                    assertEquals("Wrong status from REAL Github.com page #$id",
                        expectedStatus,rawState!!.status)
                    assertEquals("False color for REAL GitHub build #$id",
                        expectedConclusion,rawState.conclusion)
                }
                println("REAL_HTML $category #$id -> VERIFIED "+classification.label)
            }
            assertNull("HTML of one run must never classify another run",
                GithubActionHtmlParser.fromRunPage(999999999999L,html))
        }
        assertTrue(categories.containsAll(setOf("success","failure","running")))
        println("REAL_HTML summary: $verified verified, $unknown UNKNOWN; no false statuses.")
    }
}
