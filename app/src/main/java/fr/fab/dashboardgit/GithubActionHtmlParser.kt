package fr.fab.dashboardgit

import org.json.JSONArray
import org.json.JSONObject

/**
 * Conservative status extraction: proof must be attached to THIS run ID.
 * A generic "success" icon elsewhere on the page is not a verified result.
 */
object GithubActionHtmlParser {
    data class State(val status:String,val conclusion:String,val evidence:String)

    private val validStatuses=setOf("queued","pending","waiting","requested","in_progress","completed")
    private val validConclusions=setOf("success","failure","cancelled","timed_out","action_required","skipped","neutral","startup_failure")

    private fun state(statusValue:String?,conclusionValue:String?,evidence:String):State? {
        val raw=statusValue?.lowercase()?.replace('-','_')?.replace(' ','_') ?: return null
        val conclusion=conclusionValue?.lowercase()?.replace('-','_')?.replace(' ','_').orEmpty()
        val status=when(raw) {
            "success","failure","cancelled","timed_out","action_required","skipped","neutral","startup_failure" -> "completed"
            else -> raw
        }
        if(status !in validStatuses) return null
        val result=when {
            status!="completed" -> ""
            conclusion in validConclusions -> conclusion
            raw in validConclusions -> raw
            else -> ""
        }
        return State(status,result,evidence)
    }

    private fun embeddedJsonForRun(json:Any?,runId:Long):State? {
        // A bounded traversal avoids blocking the UI on a large unrelated page payload.
        var remaining=5000
        fun traverse(node:Any?,depth:Int):State? {
            if(depth>24 || remaining--<=0) return null
            when(node) {
                is JSONObject -> {
                    val idKeys=listOf("id","runId","run_id","databaseId")
                    val matched=idKeys.any { key ->
                        val id=node.opt(key)
                        id!=null && id.toString()==runId.toString()
                    }
                    if(matched) {
                        val status=node.optString("status").ifBlank { node.optString("state") }
                        val conclusion=node.optString("conclusion")
                        state(status,conclusion,"JSON lié au build #"+runId)?.let { return it }
                    }
                    val iterator=node.keys()
                    while(iterator.hasNext()) {
                        val key=iterator.next()
                        traverse(node.opt(key),depth+1)?.let { return it }
                    }
                }
                is JSONArray -> {
                    for(i in 0 until minOf(node.length(),500)) {
                        traverse(node.opt(i),depth+1)?.let { return it }
                    }
                }
            }
            return null
        }
        return traverse(json,0)
    }

    fun fromRunPage(runId:Long,html:String):State? {
        // GitHub may embed structured page data inside application/json scripts.
        val scriptPattern=Regex("""<script\b[^>]*type=["']application/json["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL))
        for(script in scriptPattern.findAll(html).take(24)) {
            val raw=script.groupValues[1].trim()
                .replace("&quot;","\"").replace("&#34;","\"").replace("&amp;","&")
            val parsed=runCatching { JSONObject(raw) }.getOrNull()
                ?: runCatching { JSONArray(raw) }.getOrNull()
                ?: continue
            embeddedJsonForRun(parsed,runId)?.let { return it }
        }

        // Explicitly annotated status on the same element as the requested run ID.
        val attrs=Regex("""<[^<>]{0,1600}\bdata-(?:run-id|workflow-run-id)=["']"""+
            runId.toString()+"""["'][^<>]{0,1600}>""",RegexOption.IGNORE_CASE)
        for(element in attrs.findAll(html).take(10)) {
            val tag=element.value
            val status=Regex("""\bdata-status=["']([^"']+)["']""",RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.getOrNull(1)
            val conclusion=Regex("""\bdata-conclusion=["']([^"']+)["']""",RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.getOrNull(1)
            state(status,conclusion,"attributs HTML du build #"+runId)?.let { return it }
        }
        return null
    }

    /** Only uses a uniquely bounded run row; never scans the full page for a green icon. */
    fun fromActionsListRow(runId:Long,html:String):State? {
        val link="/actions/runs/"+runId
        val pos=html.indexOf(link)
        if(pos<0) return null
        val rowStart=html.lastIndexOf("<li",pos)
        val rowEnd=html.indexOf("</li>",pos)
        if(rowStart<0 || rowEnd<0 || rowEnd-rowStart>18000 || pos-rowStart>15000) return null
        val row=html.substring(rowStart,rowEnd)
        val links=Regex("""/actions/runs/(\d+)""").findAll(row)
            .map { it.groupValues[1] }.distinct().toList()
        if(links.size!=1 || links[0]!=runId.toString()) return null
        val stateLabel=Regex("""(?:aria-label|title)=["'](Success|Failure|In progress|Queued|Pending|Cancelled|Timed out)["']""",
            RegexOption.IGNORE_CASE).find(row)?.groupValues?.getOrNull(1)
        return state(stateLabel,null,"ligne Actions propre au build #"+runId)
    }
}
