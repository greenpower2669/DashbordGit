package fr.fab.dashboardgit

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

/**
 * A verified status is bound to the exact GitHub Actions run ID on the same
 * anchor aria-label. Never infer a result from a CSS color or unrelated icon.
 */
data class GithubDomEvidence(
    val repo:String,
    val runId:Long,
    val url:String,
    val status:String,
    val conclusion:String,
    val branch:String,
    val message:String,
    val observedAt:Long,
    val reason:String
) {
    val verified:Boolean get()=GithubWorkflowStatus.classify(
        JSONObject().put("status",status).put("conclusion",conclusion)
    ).verified

    companion object {
        fun fromAccessibleLabel(
            repo:String,runId:Long,url:String,label:String,
            branch:String="",message:String="",
            whenObserved:Long=System.currentTimeMillis()
        ):GithubDomEvidence? {
            if(!repo.matches(Regex("[A-Za-z0-9_.-]{1,100}")) || runId<=0 ||
                !GithubMosaicTargets.isTrackedUrl(repo,url) ||
                !url.substringBefore('?').endsWith("/actions/runs/$runId"))return null
            val accessible=label.trim().lowercase(Locale.ROOT)
            val semantics=when {
                accessible.startsWith("completed successfully:") ->
                    "completed" to "success"
                accessible.startsWith("currently running:") ||
                accessible.startsWith("in progress:") -> "in_progress" to ""
                accessible.startsWith("queued:") || accessible.startsWith("waiting:") ||
                accessible.startsWith("pending:") -> "queued" to ""
                accessible.startsWith("failed:") ||
                accessible.startsWith("completed with failures:") ||
                accessible.startsWith("completed with errors:") ->
                    "completed" to "failure"
                accessible.startsWith("cancelled:") ||
                accessible.startsWith("canceled:") -> "completed" to "cancelled"
                accessible.startsWith("timed out:") -> "completed" to "timed_out"
                accessible.startsWith("action required:") -> "completed" to "action_required"
                else -> return null // No real semantic evidence => grey.
            }
            return GithubDomEvidence(repo,runId,url,semantics.first,semantics.second,
                branch.take(160),message.take(250),whenObserved,
                "aria-label GitHub pour le run #"+runId+" : "+label.take(100))
        }

        fun parseWebViewResponse(
            repo:String,pageUrl:String,callback:String,
            whenObserved:Long=System.currentTimeMillis()
        ):List<GithubDomEvidence> {
            val allowed="https://github.com/greenpower2669/$repo/actions"
            if(!GithubMosaicTargets.isGithubPage(pageUrl) ||
                pageUrl.substringBefore('?').trimEnd('/')!=allowed)return emptyList()
            val decoded=runCatching { JSONTokener(callback).nextValue() }.getOrNull()
            val list=when(decoded) {
                is String -> runCatching { JSONArray(decoded) }.getOrNull()
                is JSONArray -> decoded
                else -> null
            } ?: return emptyList()
            val found=linkedMapOf<Long,GithubDomEvidence>()
            for(i in 0 until minOf(5,list.length())) {
                val row=list.optJSONObject(i) ?: continue
                val id=row.optString("id").toLongOrNull() ?: continue
                val url=row.optString("url")
                val label=row.optString("label").ifBlank { row.optString("iconLabel") }
                // A valid run with unrecognized aria-label is still the
                // latest visible run: keep it GREY, rather than showing an
                // older successful run as fresh.
                val urlValid=GithubMosaicTargets.isTrackedUrl(repo,url) &&
                    url.substringBefore('?').endsWith("/actions/runs/$id")
                if(!urlValid)continue
                val evidence=fromAccessibleLabel(repo,id,url,label,
                    row.optString("branch"),row.optString("message"),whenObserved)
                    ?: GithubDomEvidence(repo,id,url,"unknown","",
                        row.optString("branch").take(160),
                        row.optString("message").take(250),
                        whenObserved,"Build #"+id+" visible, mais statut ARIA indéterminé")
                found[id]=evidence
            }
            return found.values.sortedByDescending { it.runId }
        }

        /** Merge only same/newer verified evidence; no stale green. */
        fun merge(snapshot:JSONObject,evidence:GithubDomEvidence):JSONObject {
            val old=snapshot.optJSONArray("runs") ?: JSONArray()
            val first=old.optJSONObject(0)
            val oldId=first?.optLong("id",-1L) ?: -1L
            if(evidence.runId<oldId)return snapshot
            // Unknown on the SAME run cannot erase proven API/DOM evidence.
            // Unknown on a NEWER run must hide an older green immediately.
            if(!evidence.verified && evidence.runId==oldId)return snapshot
            if(evidence.runId==oldId &&
                snapshot.optString("source")=="API" &&
                snapshot.optLong("savedAt",0L)>evidence.observedAt)return snapshot
            val modified=JSONObject(snapshot.toString())
            val current=if(evidence.runId==oldId && first!=null)
                JSONObject(first.toString()) else JSONObject()
            current.put("id",evidence.runId)
                .put("html_url",evidence.url)
                .put("status",evidence.status).put("conclusion",evidence.conclusion)
            if(evidence.branch.isNotBlank())current.put("head_branch",evidence.branch)
            if(evidence.message.isNotBlank()) {
                current.put("head_commit",JSONObject().put("message",evidence.message))
            }
            val runs=JSONArray().put(current)
            for(i in 0 until minOf(old.length(),4)) {
                val run=old.optJSONObject(i) ?: continue
                if(run.optLong("id",-1L)!=evidence.runId)runs.put(run)
            }
            return modified.put("runs",runs)
                .put("runId",evidence.runId)
                .put("source","CHROMIUM_DOM")
                .put("savedAt",evidence.observedAt)
                .put("needsStatusConfirmation",!evidence.verified)
        }
    }
}
