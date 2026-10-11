package fr.fab.dashboardgit

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GithubDomEvidenceTest {
    private val repo="DashbordGit"
    private val run=38132400440L
    private val url="https://github.com/greenpower2669/DashbordGit/actions/runs/$run"

    @Test fun realGithubAccessibleLabelsRecognized() {
        val success=GithubDomEvidence.fromAccessibleLabel(repo,run,url,
            "completed successfully:  Run 64 of Android APK. fix(explorer)...")
        assertEquals("success",success?.conclusion)
        assertTrue(success?.verified==true)
        val running=GithubDomEvidence.fromAccessibleLabel(repo,run,url,
            "currently running:  Run 64 of Android APK.")
        assertEquals("in_progress",running?.status)
    }

    @Test fun unrelatedColorOrWrongRunCannotClaimSuccess() {
        assertNull(GithubDomEvidence.fromAccessibleLabel(repo,run,url,"color-fg-success"))
        assertNull(GithubDomEvidence.fromAccessibleLabel(repo,run,url,"Show options"))
        assertNull(GithubDomEvidence.fromAccessibleLabel(repo,run+1,url,
            "completed successfully:"))
        assertNull(GithubDomEvidence.fromAccessibleLabel("JTrex",run,url,
            "completed successfully:"))
    }

    @Test fun parseWebViewJsonCallbackBoundToOwnRun() {
        val rows=JSONArray().put(JSONObject()
            .put("id",run.toString()).put("url",url)
            .put("label","completed successfully: Run 64 of Android APK.")
            .put("branch","feature/dashgit-home-explorer-002")
            .put("message","fix(explorer): accept canonical GitHub Actions URL variants"))
            .put(JSONObject()
                .put("id","38132400439")
                .put("url","https://evil.test/greenpower2669/DashbordGit/actions/runs/38132400439")
                .put("label","completed successfully: fake"))
        val callback=JSONObject.quote(rows.toString())
        val evidence=GithubDomEvidence.parseWebViewResponse(repo,
            "https://github.com/greenpower2669/DashbordGit/actions",callback)
        assertEquals(1,evidence.size)
        assertEquals(run,evidence.first().runId)
        assertEquals("success",evidence.first().conclusion)
        assertTrue(GithubDomEvidence.parseWebViewResponse(repo,
            "https://evil.test/DashbordGit/actions",callback).isEmpty())
    }

    @Test fun newerRunningHidesOlderGreen() {
        val oldRun=JSONObject().put("id",40L)
            .put("status","completed").put("conclusion","success")
        val snapshot=JSONObject().put("repo",repo).put("source","WEB")
            .put("runs",JSONArray().put(oldRun))
        val newer=GithubDomEvidence.fromAccessibleLabel(repo,41L,
            "https://github.com/greenpower2669/DashbordGit/actions/runs/41",
            "currently running: Run 41")!!
        val merged=GithubDomEvidence.merge(snapshot,newer)
        assertEquals(41L,merged.getJSONArray("runs").getJSONObject(0).getLong("id"))
        assertEquals("in_progress",
            merged.getJSONArray("runs").getJSONObject(0).getString("status"))
    }

    @Test fun unreadableNewestRunHidesPreviousGreen() {
        val lastSuccess=JSONObject().put("id",100L)
            .put("status","completed").put("conclusion","success")
        val before=JSONObject().put("repo",repo)
            .put("runs",JSONArray().put(lastSuccess))
        val rows=JSONArray().put(JSONObject()
            .put("id","101")
            .put("url","https://github.com/greenpower2669/DashbordGit/actions/runs/101")
            .put("label","Unknown accessible label, no supported status"))
            .put(JSONObject()
            .put("id","100")
            .put("url","https://github.com/greenpower2669/DashbordGit/actions/runs/100")
            .put("label","completed successfully: old success"))
        val evidence=GithubDomEvidence.parseWebViewResponse(repo,
            "https://github.com/greenpower2669/DashbordGit/actions",
            JSONObject.quote(rows.toString()))
        assertEquals(2,evidence.size)
        assertEquals(101L,evidence.first().runId)
        assertFalse(evidence.first().verified)
        val shown=GithubDomEvidence.merge(before,evidence.first())
        assertEquals(101L,shown.getJSONArray("runs").getJSONObject(0).getLong("id"))
        assertFalse(GithubWorkflowStatus.classify(
            shown.getJSONArray("runs").getJSONObject(0)).verified)
    }

    @Test fun olderGreenCannotOverwriteNewerRun() {
        val snapshot=JSONObject().put("repo",repo)
            .put("runs",JSONArray().put(JSONObject().put("id",60L)
                .put("status","in_progress")))
        val earlier=GithubDomEvidence.fromAccessibleLabel(repo,59L,
            "https://github.com/greenpower2669/DashbordGit/actions/runs/59",
            "completed successfully: Run 59")!!
        assertSame(snapshot,GithubDomEvidence.merge(snapshot,earlier))
    }
}
