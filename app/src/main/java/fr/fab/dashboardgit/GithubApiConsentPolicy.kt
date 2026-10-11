package fr.fab.dashboardgit

/**
 * No GitHub REST call may run without a user's affirmative one-time choice
 * or a previously granted, revocable category permission.
 */
object GithubApiConsentPolicy {
    enum class Decision { BLOCK, REQUEST, PERMIT }

    fun decide(mode:String?,categoryAllowed:Boolean):Decision = when(mode) {
        "WEB" -> Decision.BLOCK  // Explicit Web-only overrides any old permission.
        "ASK" -> if(categoryAllowed) Decision.PERMIT else Decision.REQUEST
        "ALLOWED" -> if(categoryAllowed) Decision.PERMIT else Decision.BLOCK
        else -> Decision.BLOCK // Unknown or corrupted settings must fail closed.
    }
}
