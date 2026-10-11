package fr.fab.dashboardgit

import org.junit.Test
import org.junit.Assert.assertEquals

class GithubApiConsentPolicyTest {
    @Test fun explicitWebOnlyNeverCallsApi() {
        assertEquals(GithubApiConsentPolicy.Decision.BLOCK,
            GithubApiConsentPolicy.decide("WEB",true))
        assertEquals(GithubApiConsentPolicy.Decision.BLOCK,
            GithubApiConsentPolicy.decide("WEB",false))
    }

    @Test fun unknownPermissionFailsClosed() {
        assertEquals(GithubApiConsentPolicy.Decision.BLOCK,
            GithubApiConsentPolicy.decide(null,false))
        assertEquals(GithubApiConsentPolicy.Decision.BLOCK,
            GithubApiConsentPolicy.decide("unexpected",true))
    }

    @Test fun oneTimeOrPersistentConsentRequired() {
        assertEquals(GithubApiConsentPolicy.Decision.REQUEST,
            GithubApiConsentPolicy.decide("ASK",false))
        assertEquals(GithubApiConsentPolicy.Decision.PERMIT,
            GithubApiConsentPolicy.decide("ASK",true))
        assertEquals(GithubApiConsentPolicy.Decision.BLOCK,
            GithubApiConsentPolicy.decide("ALLOWED",false))
        assertEquals(GithubApiConsentPolicy.Decision.PERMIT,
            GithubApiConsentPolicy.decide("ALLOWED",true))
    }
}
