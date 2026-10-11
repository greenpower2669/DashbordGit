package fr.fab.dashboardgit

import org.junit.Assert.*
import org.junit.Test

class GithubMosaicTargetsTest {
    @Test fun specificRealRunLinksArePreserved() {
        val actual=GithubMosaicTargets.target(
            "DashbordGit",GithubMosaicTargets.Kind.RUN,38122695593L,null)
        assertEquals(
            "https://github.com/greenpower2669/DashbordGit/actions/runs/38122695593",
            actual)
        assertTrue(GithubMosaicTargets.isTrackedUrl("DashbordGit",actual))
        assertFalse(GithubMosaicTargets.isTrackedUrl("JTrex",actual))
    }

    @Test fun invalidHostsAndNamesNeverBecomeNavigationTargets() {
        assertFalse(GithubMosaicTargets.isGithubPage("https://github.com.evil.test/a"))
        assertFalse(GithubMosaicTargets.isGithubPage("http://github.com/a"))
        assertFalse(GithubMosaicTargets.isGithubPage("https://user:pass@github.com/a"))
        assertFalse(GithubMosaicTargets.isGithubPage("javascript:alert(1)"))
        assertFalse(GithubMosaicTargets.isTrackedUrl("DashbordGit",
            "https://github.com/attacker/DashbordGit/actions"))
        assertThrows(IllegalArgumentException::class.java) {
            GithubMosaicTargets.target("../steal",GithubMosaicTargets.Kind.RUN,42,null)
        }
    }

    @Test fun missingRunUsesOfficialActionsPageNotFabricatedId() {
        assertEquals("https://github.com/greenpower2669/GeckoDoku/actions",
            GithubMosaicTargets.target("GeckoDoku",GithubMosaicTargets.Kind.RUN,-1,null))
        val sha="a".repeat(40)
        assertEquals("https://github.com/greenpower2669/JTrex/commit/$sha",
            GithubMosaicTargets.target("JTrex",GithubMosaicTargets.Kind.COMMITS,null,sha))
    }
}
