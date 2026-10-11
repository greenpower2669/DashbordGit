package fr.fab.dashboardgit

import org.junit.Assert.*
import org.junit.Test

class GithubExplorerPlanTest {
    @Test fun publicActionsPagesOnlyAndNoRestEndpoints() {
        val plan=GithubExplorerPlan.create(
            listOf("SpaceFortressVs","GeckoDoku","JT rex","GeckoDoku","JT rex/evil","JTrex"))
        assertEquals(listOf("SpaceFortressVs","GeckoDoku","JTrex"),
            plan.map { it.repository })
        for(entry in plan) {
            assertTrue(GithubMosaicTargets.isGithubPage(entry.url))
            assertTrue(entry.url.endsWith("/actions"))
            assertFalse(entry.url.contains("api.github.com"))
        }
    }

    @Test fun boundedBrowsingAndFixedCourtesyGap() {
        val repositories=(1..30).map { "repo$it" }
        assertEquals(GithubExplorerPlan.MAX_VISIBLE_PAGES,
            GithubExplorerPlan.create(repositories).size)
        assertTrue(GithubExplorerPlan.MIN_GAP_MS>=3000L)
        assertTrue(GithubExplorerPlan.create(emptyList()).isEmpty())
    }
}
