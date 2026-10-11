package fr.fab.dashboardgit

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Downloaded LIVE from actual public GitHub Actions pages in CI.
 * No invented HTML. UNKNOWN is allowed, FALSE GREEN is not.
 */
class GithubActionHtmlRealSiteTest {
    @Test fun realPublicGithubPagesMatchIndependentApiTruthOrStayUnknown() {
        val root=File("src/test/resources/github_live")
        val manifest=File(root,"manifest.json")
        assertTrue("Missing live GitHub HTML fixtures: CI must download real pages first",manifest.isFile)
        val cases=JSONArray(manifest.readText())
        assertTrue("Expected both live success and failure samples",cases.length()>=2)
        val categories=linkedSetOf<String>()
        var parsed=0
        var unknown=0
        for(i in 0 until cases.length()) {
            val item=cases.getJSONObject(i)
            val expected=item.getString("expectedConclusion")
            val id=item.getLong("runId")
            val sample=File(root,"$expected-$id.html")
            assertTrue("Missing real GitHub HTML "+sample.name,sample.isFile)
            val html=sample.readText()
            assertTrue("Live HTML is unexpectedly too small",html.length>10_000)
            categories.add(expected)

            val state=GithubActionHtmlParser.fromRunPage(id,html)
            if(state==null || (state.status=="completed" && state.conclusion.isBlank())) {
                unknown++
                println("REAL_HTML $expected #$id -> UNKNOWN: API consent required")
            } else {
                parsed++
                assertEquals("Wrong status for real run #$id","completed",state.status)
                assertEquals("FALSE GITHUB STATUS on real run #$id",expected,state.conclusion)
                println("REAL_HTML $expected #$id -> VERIFIED "+state.status+"/"+state.conclusion)
            }

            // This real page must not attribute its status to another run.
            val wrong=GithubActionHtmlParser.fromRunPage(999999999999L,html)
            assertNull("A real page classified an unrelated run!",wrong)
        }
        assertTrue(categories.containsAll(setOf("success","failure")))
        println("REAL_HTML summary: $parsed verified, $unknown UNKNOWN.")
        println("UNKNOWN is NOT GREEN; the app must ask for API authorization.")
    }
}
