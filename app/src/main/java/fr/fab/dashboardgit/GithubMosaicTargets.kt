package fr.fab.dashboardgit

import java.net.URI

/**
 * Only top-level public GitHub pages, never iframes or API queries.
 * The exact target is retained even if the remote page has internal navigation.
 */
object GithubMosaicTargets {
    enum class Kind { RUN, ACTIONS, COMMITS }

    private val repoRegex=Regex("[A-Za-z0-9_.-]{1,100}")
    private val shaRegex=Regex("[0-9a-fA-F]{40}")

    fun kind(value:String?):Kind =
        runCatching { Kind.valueOf(value ?: "") }.getOrDefault(Kind.ACTIONS)

    fun target(repo:String,kind:Kind,runId:Long?,sha:String?):String {
        require(repoRegex.matches(repo)) { "Nom de dépôt invalide" }
        val base="https://github.com/greenpower2669/$repo"
        return when(kind) {
            Kind.RUN -> if(runId!=null && runId>0L) "$base/actions/runs/$runId"
                else "$base/actions"
            Kind.ACTIONS -> "$base/actions"
            Kind.COMMITS -> if(sha!=null && shaRegex.matches(sha))
                "$base/commit/$sha" else "$base/commits"
        }
    }

    fun isGithubPage(url:String):Boolean = try {
        val uri=URI(url)
        uri.scheme=="https" &&
            uri.host?.equals("github.com",ignoreCase=true)==true &&
            uri.port==-1 && uri.userInfo==null
    } catch(_:Exception) { false }

    fun isTrackedUrl(repo:String,url:String):Boolean {
        if(!repoRegex.matches(repo) || !isGithubPage(url)) return false
        val path=URI(url).path
        val base="/greenpower2669/$repo/"
        if(!path.startsWith(base)) return false
        val suffix=path.removePrefix(base)
        return suffix=="actions" ||
            suffix=="commits" ||
            Regex("actions/runs/[0-9]+").matches(suffix) ||
            Regex("commit/[0-9a-fA-F]{40}").matches(suffix)
    }
}
