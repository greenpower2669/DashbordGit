package fr.fab.dashboardgit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Read-only public GitHub Web reader. Does not call api.github.com.
 * Every network call is initiated only by a user-triggered action.
 *
 * Optional .dashboardgit.json manifests are requested ONLY when advertised by
 * a visible public branch or a repository link. No speculative 3x404 probing.
 */
class GithubWebSource(
    context:Context,
    private val log:(String)->Unit
) {
    private val owner="greenpower2669"
    private val legalName=Regex("[A-Za-z0-9_.-]{1,100}")
    private val preferences=context.getSharedPreferences("dashboardgit_web_sources",Context.MODE_PRIVATE)

    private fun read(url:String,accept:String="text/html",quiet404:Boolean=false):String? {
        val parsed=URL(url)
        require(parsed.protocol=="https" &&
            parsed.host in setOf("github.com","raw.githubusercontent.com")) {
            "Hôte Web non autorisé"
        }
        val connection=(parsed.openConnection() as HttpURLConnection).apply {
            connectTimeout=8500
            readTimeout=10500
            instanceFollowRedirects=false
            setRequestProperty("User-Agent","Mozilla/5.0 (compatible; DashbordGit Android Web status reader)")
            setRequestProperty("Accept",accept)
            setRequestProperty("Accept-Language","en-US,en;q=0.9")
        }
        try {
            val code=connection.responseCode
            if(code !in 200..299) {
                if(code!=404 || !quiet404) log("WEB HTTP "+code+" : "+parsed.path.take(110))
                return null
            }
            val text=StringBuilder()
            connection.inputStream.bufferedReader().use { input ->
                val buffer=CharArray(8192)
                while(text.length<2_500_000) {
                    val n=input.read(buffer)
                    if(n<0) break
                    text.append(buffer,0,minOf(n,2_500_000-text.length))
                }
            }
            return text.toString()
        } finally {
            connection.disconnect()
        }
    }

    fun listPublicRepos():List<String> {
        val repos=linkedSetOf<String>()
        val pattern=Regex("""/greenpower2669/([A-Za-z0-9_.-]+)(?:["'/?#]|&quot;|\\u0022)""")
        for(page in 1..3) {
            val html=runCatching {
                read("https://github.com/"+owner+"?tab=repositories&page="+page)
            }.getOrNull() ?: continue
            pattern.findAll(html).forEach {
                val name=it.groupValues[1]
                if(legalName.matches(name) && name !in setOf("followers","following","stars","tab")) {
                    repos.add(name)
                }
            }
            if(repos.size>=100) break
        }
        log("WEB : "+repos.size+" dépôts publics détectés")
        return repos.toList()
    }

    private fun manifestCandidates(repo:String,home:String?,branches:String?):List<String> {
        val candidates=linkedSetOf<String>()
        val cached=preferences.getString("manifest_"+repo,null)
        if(cached!=null && cached.startsWith("https://raw.githubusercontent.com/"+owner+"/"+repo+"/")) {
            candidates.add(cached)
        }

        // Explicit published branch: the producer runs only after being installed.
        val pattern=Regex("/"+owner+"/"+Regex.escape(repo)+"/tree/dashboardgit-status(?:[\"'/?#]|&quot;)")
        if(branches!=null && pattern.containsMatchIn(branches)) {
            candidates.add("https://raw.githubusercontent.com/"+owner+"/"+repo+
                "/dashboardgit-status/.dashboardgit.json")
        }
        // A conventional manifest explicitly linked from the project's main page.
        val directLink=Regex("/"+owner+"/"+Regex.escape(repo)+
            "/(?:blob|raw)/(main|master)/\\.dashboardgit\\.json")
        if(home!=null) {
            directLink.findAll(home).forEach {
                candidates.add("https://raw.githubusercontent.com/"+owner+"/"+repo+"/"+
                    it.groupValues[1]+"/.dashboardgit.json")
            }
        }
        return candidates.toList()
    }

    private fun readAdvertisedJson(repo:String,home:String?,branches:String?):JSONObject? {
        val candidates=manifestCandidates(repo,home,branches)
        if(candidates.isEmpty()) {
            log("JSON "+repo+" : aucun manifeste public annoncé, aucune requête inutile")
            return null
        }
        for(url in candidates) {
            val content=runCatching { read(url,"application/json",quiet404=true) }
                .getOrNull() ?: continue
            val json=runCatching { JSONObject(content) }.getOrNull()
            if(json==null || json.optInt("schemaVersion",-1)!=1 ||
                json.optString("repo")!=repo) {
                log("JSON "+repo+" : contenu invalide, ignoré")
                continue
            }
            preferences.edit().putString("manifest_"+repo,url).apply()
            json.put("jsonSource",url)
            log("JSON "+repo+" : manifeste vérifié lu")
            return json
        }
        preferences.edit().remove("manifest_"+repo).apply()
        log("JSON "+repo+" : manifeste annoncé mais inaccessible")
        return null
    }

    private fun readActionRuns(repo:String):JSONArray {
        val base="https://github.com/"+owner+"/"+repo+"/actions"
        val html=runCatching { read(base) }.getOrNull() ?: return JSONArray()
        val pattern=Regex("/"+owner+"/"+Regex.escape(repo)+"/actions/runs/(\\d+)")
        val ids=pattern.findAll(html).mapNotNull {
            it.groupValues[1].toLongOrNull()
        }.distinct().sortedDescending().take(5).toList()
        log("WEB Actions "+repo+" : "+ids.size+" identifiant(s) de build")
        val result=JSONArray()
        for((index,id) in ids.withIndex()) {
            val run=JSONObject().put("id",id).put("name","GitHub Actions")
                .put("status","unknown").put("conclusion","")
                .put("html_url",base+"/runs/"+id)
            val fromRow=GithubActionHtmlParser.fromActionsListRow(id,html)
            var state=fromRow
            if(state==null && index<2) {
                val detail=runCatching { read(run.getString("html_url")) }.getOrNull()
                if(detail!=null) state=GithubActionHtmlParser.fromRunPage(id,detail)
            }
            if(state!=null) {
                run.put("status",state.status).put("conclusion",state.conclusion)
                log("WEB Actions "+repo+" #"+id+" : "+
                    state.status+" / "+state.conclusion+" ("+state.evidence+")")
            } else {
                log("WEB Actions "+repo+" #"+id+" : état HTML non identifiable")
            }
            result.put(run)
        }
        return result
    }

    private fun extractPublicMetadata(repo:String,home:String?,branches:String?,snapshot:JSONObject) {
        if(home!=null) {
            val sha=Regex("/"+owner+"/"+Regex.escape(repo)+"/commit/([0-9a-fA-F]{40})")
                .find(home)?.groupValues?.getOrNull(1)
            if(!sha.isNullOrBlank()) snapshot.put("latestCommit",sha)
            val updated=Regex("""<relative-time[^>]*datetime=["']([^"']+)["']""")
                .find(home)?.groupValues?.getOrNull(1)
            if(!updated.isNullOrBlank()) snapshot.put("webCommitTime",updated)
        }
        if(branches!=null) {
            val branchPattern=Regex("/"+owner+"/"+Regex.escape(repo)+"/tree/([A-Za-z0-9_.%/-]+)")
            val names=branchPattern.findAll(branches)
                .map {
                    java.net.URLDecoder.decode(it.groupValues[1].replace("+","%2B"),"UTF-8")
                }
                .filter { it.isNotBlank() && !it.contains("..") && it.length<=200 }
                .distinct().take(20).toList()
            if(names.isNotEmpty()) {
                snapshot.put("webBranches",JSONArray(names))
                if(snapshot.optInt("branchCount",-1)<0) snapshot.put("branchCount",names.size)
            }
        }
        val releases=runCatching {
            read("https://github.com/"+owner+"/"+repo+"/releases")
        }.getOrNull()
        if(releases!=null) {
            val tag=Regex("/"+owner+"/"+Regex.escape(repo)+
                "/releases/tag/([A-Za-z0-9._%+-]+)")
                .find(releases)?.groupValues?.getOrNull(1)
            if(!tag.isNullOrBlank()) snapshot.put("latestRelease",tag)
        }
    }

    private fun safeRun(run:JSONObject?):Boolean {
        if(run==null) return false
        val status=run.optString("status","unknown")
        val conclusion=run.optString("conclusion","")
        return when(status) {
            "queued","pending","waiting","requested","in_progress" -> true
            "completed" -> conclusion in setOf("success","failure","cancelled","timed_out",
                "skipped","action_required","neutral","startup_failure")
            else -> false
        }
    }

    fun inspect(repo:String):JSONObject {
        require(legalName.matches(repo)) { "Nom de dépôt non conforme" }
        val snapshot=JSONObject()
            .put("repo",repo).put("savedAt",System.currentTimeMillis())
            .put("source","WEB").put("runs",JSONArray())
            .put("runId",-1L).put("branchCount",-1)
            .put("buildKnown",false).put("mergeKnown",false).put("releaseKnown",false)
        val home=runCatching {
            read("https://github.com/"+owner+"/"+repo)
        }.getOrNull()
        val branches=runCatching {
            read("https://github.com/"+owner+"/"+repo+"/branches")
        }.getOrNull()

        val json=readAdvertisedJson(repo,home,branches)
        if(json!=null) {
            snapshot.put("source","JSON")
                .put("jsonUpdatedAt",json.optString("updatedAt"))
                .put("jsonSource",json.optString("jsonSource"))
            for(key in listOf("branchCount","artifactName","latestCommit","latestRelease",
                "buildNeeded","mergeNeeded","releaseNeeded",
                "buildKnown","mergeKnown","releaseKnown")) {
                if(json.has(key)) snapshot.put(key,json.get(key))
            }
            val jsonRuns=json.optJSONArray("runs") ?: JSONArray()
            snapshot.put("runs",jsonRuns)
            if(jsonRuns.length()>0) snapshot.put("runId",
                jsonRuns.optJSONObject(0)?.optLong("id",-1L) ?: -1L)
        }
        extractPublicMetadata(repo,home,branches,snapshot)

        // A more recent run seen on the Web hides old JSON success/failure
        // even when the newest run status cannot be extracted.
        val webRuns=readActionRuns(repo)
        if(webRuns.length()>0) {
            val webLatest=webRuns.optJSONObject(0)
            val webId=webLatest?.optLong("id",-1L) ?: -1L
            val jsonLatest=snapshot.optJSONArray("runs")?.optJSONObject(0)
            val jsonId=jsonLatest?.optLong("id",-1L) ?: -1L
            val jsonCompleted=jsonLatest?.optString("status")=="completed" &&
                safeRun(jsonLatest)
            val webCompleted=webLatest?.optString("status")=="completed" &&
                safeRun(webLatest)
            when {
                webId>jsonId -> {
                    snapshot.put("runs",webRuns).put("runId",webId).put("source","WEB")
                }
                webId==jsonId && jsonLatest!=null && jsonCompleted && !webCompleted -> {
                    // Same run, last JSON confirms a completed result; HTML may
                    // have only a partial status. Keep it explicitly LAST KNOWN.
                    log("JSON "+repo+" #"+jsonId+" : résultat confirmé, HTML incomplet")
                }
                webId==jsonId && jsonLatest!=null && !safeRun(webLatest) &&
                    safeRun(jsonLatest) -> {
                    log("JSON "+repo+" #"+jsonId+" : statut conservé depuis le manifeste")
                }
                webId>=jsonId -> {
                    snapshot.put("runs",webRuns).put("runId",webId).put("source","WEB")
                }
            }
        }
        val latest=snapshot.optJSONArray("runs")?.optJSONObject(0)
        if(!safeRun(latest)) {
            snapshot.put("needsStatusConfirmation",true)
            log("WEB "+repo+" : dernier build non confirmé, API possible sur autorisation")
        } else {
            snapshot.put("needsStatusConfirmation",false)
        }
        return snapshot
    }
}
