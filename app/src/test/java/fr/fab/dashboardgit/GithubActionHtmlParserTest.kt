package fr.fab.dashboardgit

import org.junit.Assert.*
import org.junit.Test

class GithubActionHtmlParserTest {
    @Test fun successOnlyWhenIdMatches() {
        val html="""<html><script type="application/json">{"payload":{"run":{"id":21,"status":"completed","conclusion":"success"}}}</script></html>"""
        assertEquals("success",GithubActionHtmlParser.fromRunPage(21,html)?.conclusion)
        assertNull(GithubActionHtmlParser.fromRunPage(22,html))
    }

    @Test fun failureAndRunningAndWaiting() {
        val bad="""<script type="application/json">{"run":{"id":21,"status":"completed","conclusion":"failure"}}</script>"""
        val running="""<script type="application/json">{"run":{"id":22,"status":"in_progress","conclusion":null}}</script>"""
        val wait="""<div data-run-id="23" data-status="queued"></div>"""
        assertEquals("failure",GithubActionHtmlParser.fromRunPage(21,bad)?.conclusion)
        assertEquals("in_progress",GithubActionHtmlParser.fromRunPage(22,running)?.status)
        assertEquals("queued",GithubActionHtmlParser.fromRunPage(23,wait)?.status)
    }

    @Test fun unrelatedGreenDoesNotFalselyMarkCurrentBuild() {
        val html="""<span aria-label="Success"></span>
          <script type="application/json">{"runs":[{"id":20,"status":"completed","conclusion":"success"}]}</script>
          <h1>Run 21</h1>"""
        assertNull(GithubActionHtmlParser.fromRunPage(21,html))
        assertNull(GithubActionHtmlParser.fromActionsListRow(21,html))
    }

    @Test fun boundedListRowWithOneRun() {
        val html="""<ul><li><span aria-label="In progress"></span><a href="/actions/runs/22">build</a></li>
          <li><span aria-label="Success"></span><a href="/actions/runs/21">build</a></li></ul>"""
        assertEquals("success",GithubActionHtmlParser.fromActionsListRow(21,html)?.conclusion)
        assertEquals("in_progress",GithubActionHtmlParser.fromActionsListRow(22,html)?.status)
    }

    @Test fun rowWithSeveralRunsIsAmbiguous() {
        val html="""<li><span aria-label="Success"></span><a href="/actions/runs/21">one</a>
          <a href="/actions/runs/22">other</a></li>"""
        assertNull(GithubActionHtmlParser.fromActionsListRow(21,html))
    }

    @Test fun incompleteCompletedIsNotGreen() {
        val html="""<script type="application/json">{"workflow_run":{"run_id":21,"status":"completed","conclusion":null}}</script>"""
        val value=GithubActionHtmlParser.fromRunPage(21,html)
        assertEquals("completed",value?.status)
        assertEquals("",value?.conclusion)
    }
}
