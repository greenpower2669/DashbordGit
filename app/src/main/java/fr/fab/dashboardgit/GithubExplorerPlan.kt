package fr.fab.dashboardgit

/**
 * Public human-readable URL targets, never GitHub REST endpoints.
 * A home preview is supplemental: its own Chromium visits can issue
 * internal GitHub requests and must never be misreported as free traffic.
 */
object GithubExplorerPlan {
    const val MAX_VISIBLE_PAGES=12
    const val MIN_GAP_MS=3000L

    data class Visit(val repository:String,val url:String)

    fun create(repositories:Collection<String>):List<Visit> {
        val safe=Regex("[A-Za-z0-9_.-]{1,100}")
        return repositories.asSequence()
            .filter { safe.matches(it) }
            .distinct()
            .take(MAX_VISIBLE_PAGES)
            .map { Visit(it,"https://github.com/greenpower2669/$it/actions") }
            .toList()
    }
}
