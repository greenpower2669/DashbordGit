package fr.fab.dashboardgit

import org.json.JSONObject

/**
 * One status contract for public HTML, published JSON, authorised REST API,
 * UI cards and "verified vs unknown" reporting.
 *
 * "completed" alone is NOT a confirmed successful build.
 */
object GithubWorkflowStatus {
    data class Result(
        val marker:String,
        val label:String,
        val verified:Boolean
    )

    fun classify(run:JSONObject?):Result {
        if(run==null) return Result("⚪","NON VÉRIFIÉ",false)
        val status=run.optString("status","unknown").trim().lowercase()
        val conclusion=run.optString("conclusion","").trim().lowercase()
        return when {
            status in setOf("queued","pending","waiting","requested") ->
                Result("🟠","EN ATTENTE",true)
            status=="in_progress" ->
                Result("🟡","EN COURS",true)
            status=="completed" && conclusion=="success" ->
                Result("🟢","RÉUSSI",true)
            status=="completed" && conclusion in setOf("failure","timed_out","startup_failure") ->
                Result("🔴","ÉCHEC",true)
            status=="completed" && conclusion=="action_required" ->
                Result("🟠","ACTION REQUISE",true)
            status=="completed" && conclusion=="cancelled" ->
                Result("⚪","ANNULÉ",true)
            status=="completed" && conclusion in setOf("skipped","neutral") ->
                Result("⚪","IGNORÉ",true)
            else -> Result("⚪","NON VÉRIFIÉ",false)
        }
    }
}
