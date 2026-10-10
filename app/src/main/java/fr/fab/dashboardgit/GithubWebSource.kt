package fr.fab.dashboardgit

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Reads only public HTML and raw GitHub JSON, never api.github.com. */
class GithubWebSource(private val log:(String)->Unit) {
    private val owner="greenpower2669"
    private val legalName=Regex("[A-Za-z0-9_.-]{1,100}")

    private fun read(url:String, accept:String="text/html"):String? {
        val parsed=URL(url)
        require(parsed.protocol=="https" && parsed.host in setOf("github.com","raw.githubusercontent.com")) {
            "Web host not allowed"
        }
        val connection=(parsed.openConnection() as HttpURLConnection).apply {
            connectTimeout=8500
            readTimeout=10500
            instanceFollowRedirects=false
            setRequestProperty("User-Agent","Mozilla/5.0 (DashbordGit Android; public GitHub reader)")
            setRequestProperty("Accept",accept)
            setRequestProperty("Accept-Language","en-US,en;q=0.9")
        }
        try {
            val code=connection.responseCode
            if(code !in 200..299) {
                log("WEB HTTP "+code+" : "+parsed.path.take(100))
                return null
            }
            val builder=StringBuilder()
            connection.inputStream.bufferedReader().use { input ->
                val buffer=CharArray(8192)
                while(builder.length<2_500_000) {
                    val length=input.read(buffer)
                    if(length<0) break
                    builder.append(buffer,0,minOf(length,2_500_000-builder.length))
                }
            }
            return builder.toString()
        } finally { connection.disconnect() }
    }

    fun listPublicRepos():List<String> {
        val repos=linkedSetOf<String>()
        val pattern=Regex("""/greenpower2669/([A-Za-z0-9_.-]+)(?:["'/?#]|&quot;|\\u0022)""")
        for(page in 1..3) {
            val url="https://github.com/"+owner+"?tab=repositories&page="+page
            val html=runCatching { read(url) }.getOrNull() ?: continue
            pattern.findAll(html).forEach {
                val name=it.groupValues[1]
                if(legalName.matches(name) && name !in setOf("followers","following","stars","tab")) repos.add(name)
            }
            if(repos.size>=100) break
        }
        log("WEB repositories: "+repos.size)
        return repos.toList()
    }

    private fun readPublicJson(repo:String):JSONObject? {
        for(branch in listOf("dashboardgit-status","main","master")) {
            val url="https://raw.githubusercontent.com/"+owner+"/"+repo+"/"+branch+"/.dashboardgit.json"
            val data=runCatching { read(url,"application/json") }.getOrNull() ?: continue
            val json=runCatching { JSONObject(data) }.getOrNull() ?: continue
            if(json.optInt("schemaVersion",-1)!=1 || json.optString("repo")!=repo) {
                log("JSON "+repo+" rejected: invalid format or repo")
                continue
            }
            json.put("jsonSource",url)
            return json
        }
        return null
    }

    private fun statusFromRunPage(html:String):Pair<String,String>? {
        val page=html.replace("&quot;","\"").replace("&#39;","'")
        val status=Regex(""""status"\s*:\s*"(queued|pending|waiting|in_progress|completed|requested)"""",RegexOption.IGNORE_CASE)
            .find(page)?.groupValues?.getOrNull(1)?.lowercase()
        val conclusion=Regex(""""conclusion"\s*:\s*"(success|failure|cancelled|timed_out|action_required|skipped)"""",RegexOption.IGNORE_CASE)
            .find(page)?.groupValues?.getOrNull(1)?.lowercase().orEmpty()
        if(status!=null) return status to conclusion
        // Some versions of GitHub expose an explicit run status in accessible labels.
        val label=Regex("""(?:aria-label|title)=["'](In progress|Queued|Pending|Waiting|Success|Failure|Cancelled|Timed out)["']""",RegexOption.IGNORE_CASE)
            .find(page)?.groupValues?.getOrNull(1)?.lowercase() ?: return null
        return when(label) {
            "success" -> "completed" to "success"
            "failure" -> "completed" to "failure"
            "cancelled" -> "completed" to "cancelled"
            "timed out" -> "completed" to "timed_out"
            "in progress" -> "in_progress" to ""
            else -> "queued" to ""
        }
    }

    private fun readActionRuns(repo:String):JSONArray {
        val html=runCatching { read("https://github.com/"+owner+"/"+repo+"/actions") }.getOrNull()
            ?: return JSONArray()
        val pattern=Regex("/"+owner+"/"+Regex.escape(repo)+"/actions/runs/(\\d+)")
        val ids=pattern.findAll(html).mapNotNull { it.groupValues[1].toLongOrNull() }
            .distinct().sortedDescending().take(5).toList()
        val result=JSONArray()
        ids.forEachIndexed { index,id ->
            val run=JSONObject().put("id",id).put("name","GitHub Actions")
                .put("html_url","https://github.com/"+owner+"/"+repo+"/actions/runs/"+id)
                .put("status","unknown").put("conclusion","")
            // Limit detailed HTML requests; don't overwhelm a public service.
            if(index<2) {
                val detail=runCatching { read(run.getString("html_url")) }.getOrNull()
                detail?.let { statusFromRunPage(it) }?.let {
                    run.put("status",it.first).put("conclusion",it.second)
                }
            }
            result.put(run)
        }
        return result
    }

    private fun complementWithPublicWeb(repo:String,snapshot:JSONObject) {
        val home=runCatching { read("https://github.com/"+owner+"/"+repo) }.getOrNull()
        if(home!=null) {
            // SHA visible on the public repository page, without REST consumption.
            val sha=Regex("/"+owner+"/"+Regex.escape(repo)+"/commit/([0-9a-fA-F]{40})")
                .find(home)?.groupValues?.getOrNull(1)
            if(!sha.isNullOrBlank()) snapshot.put("latestCommit",sha)
            val updated=Regex("""<relative-time[^>]*datetime=["']([^"']+)["']""")
                .find(home)?.groupValues?.getOrNull(1)
            if(!updated.isNullOrBlank()) snapshot.put("webCommitTime",updated)
        }
        // No fabricated branch count / merge verdict if HTML is too dynamic.
        val releasePage=runCatching { read("https://github.com/"+owner+"/"+repo+"/releases") }.getOrNull()
        if(releasePage!=null) {
            val tag=Regex("/"+owner+"/"+Regex.escape(repo)+"/releases/tag/([A-Za-z0-9._%+-]+)")
                .find(releasePage)?.groupValues?.getOrNull(1)
            if(!tag.isNullOrBlank()) snapshot.put("latestRelease",tag)
        }
    }

    fun inspect(repo:String):JSONObject {
        require(legalName.matches(repo)) { "Invalid repository name" }
        val snapshot=JSONObject()
            .put("repo",repo).put("savedAt",System.currentTimeMillis())
            .put("source","WEB").put("runs",JSONArray())
            .put("runId",-1).put("branchCount",-1)
            .put("buildKnown",false).put("mergeKnown",false).put("releaseKnown",false)
        val json=readPublicJson(repo)
        if(json!=null) {
            snapshot.put("source","JSON")
                .put("jsonUpdatedAt",json.optString("updatedAt"))
                .put("jsonSource",json.optString("jsonSource"))
            for(key in listOf("branchCount","artifactName","latestCommit","latestRelease",
                "buildNeeded","mergeNeeded","releaseNeeded","buildKnown","mergeKnown","releaseKnown")) {
                if(json.has(key)) snapshot.put(key,json.get(key))
            }
            val runList=json.optJSONArray("runs") ?: JSONArray()
            snapshot.put("runs",runList)
            if(runList.length()>0) snapshot.put("runId",runList.optJSONObject(0)?.optLong("id",-1L) ?: -1L)
        }
        complementWithPublicWeb(repo,snapshot)
        val webRuns=readActionRuns(repo)
        if(webRuns.length()>0) {
            val webId=webRuns.optJSONObject(0)?.optLong("id",-1L) ?: -1L
            val jsonRun=snapshot.optJSONArray("runs")?.optJSONObject(0)
            val jsonId=jsonRun?.optLong("id",-1L) ?: -1L
            if(webId>=jsonId && webId>0) {
                snapshot.put("runs",webRuns).put("runId",webId).put("source","WEB")
                if(webId==jsonId && webRuns.optJSONObject(0)?.optString("status")=="unknown") {
                    snapshot.put("lastKnownConclusion",jsonRun?.optString("conclusion").orEmpty())
                }
            }
        }
        if(snapshot.optJSONArray("runs")?.length()==0) log(repo+" : no proved current workflow status")
        return snapshot
    }
}
