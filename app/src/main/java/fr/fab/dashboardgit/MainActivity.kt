package fr.fab.dashboardgit

import android.os.Bundle
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.regex.Pattern

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var list: LinearLayout
    private lateinit var info: TextView
    private var tts: TextToSpeech? = null
    private val selected = linkedSetOf<String>()
    private val debugEvents=mutableListOf<String>()
    private fun debug(msg:String) {
        val safe=msg.replace(Regex("(?i)(authorization|bearer|token)\\s*[:=]\\s*\\S+"), "[SECRET]")
        synchronized(debugEvents) {
            debugEvents.add(SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.FRANCE).format(Date())+" | "+safe.take(500))
            if(debugEvents.size>500) debugEvents.removeAt(0)
        }
    }
    private val prefs by lazy { getSharedPreferences("dashboardgit", MODE_PRIVATE) }
    private val cache by lazy { getSharedPreferences("dashboardgit_cache", MODE_PRIVATE) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val configuredEngine=prefs.getString("tts_engine","").orEmpty()
        tts = if(configuredEngine.isBlank()) TextToSpeech(this,this) else TextToSpeech(this,this,configuredEngine)
        selected.addAll(prefs.getStringSet("selected_repos", emptySet()) ?: emptySet())
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,28,28,28) }
        val title=TextView(this).apply { text="● DashbordGit"; textSize=30f }
        val controls=LinearLayout(this)
        val settings=Button(this).apply { text="⚙ Projets"; setOnClickListener { chooseProjects() } }
        val refresh=Button(this).apply { text="🔄 ACTUALISER"; setOnClickListener { refreshSelected() } }
        controls.addView(settings); controls.addView(refresh)
        val extras=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        extras.addView(Button(this).apply { text="💾 EXPORTER SAUVEGARDE"; setOnClickListener { exportFile(101) } })
        extras.addView(Button(this).apply { text="📥 IMPORTER SAUVEGARDE"; setOnClickListener { importFile() } })
        extras.addView(Button(this).apply { text="🧾 EXPORTER JOURNAL DEBUG"; setOnClickListener { exportFile(102) } })
        extras.addView(Button(this).apply { text="🔊 VOIX SHERPA / KOKORO"; setOnClickListener { chooseVoice() } })
        info=TextView(this).apply { text="Choisis les projets avec ⚙ puis appuie sur ACTUALISER."; textSize=18f; setPadding(0,20,0,20) }
        val scroll=ScrollView(this)
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(title); root.addView(controls); root.addView(extras); root.addView(info); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        selected.mapNotNull { loadSnapshot(it) }.sortedByDescending { it.optLong("savedAt") }.forEach { renderSnapshot(it,true) }
        debug("Ouverture : ${selected.size} projets")
    }

    private fun chooseProjects() = Thread {
        try {
            val repos=getJsonArray("https://api.github.com/users/greenpower2669/repos?per_page=100&sort=updated")
            val names=(0 until repos.length()).map { repos.getJSONObject(it).getString("name") }
            runOnUiThread {
                val checked=BooleanArray(names.size){ selected.contains(names[it]) }
                android.app.AlertDialog.Builder(this).setTitle("Projets à surveiller")
                    .setMultiChoiceItems(names.toTypedArray(),checked){_,i,on-> if(on)selected.add(names[i]) else selected.remove(names[i]) }
                    .setPositiveButton("OK"){_,_-> prefs.edit().putStringSet("selected_repos", selected.toSet()).apply(); info.text="${selected.size} projet(s) sélectionné(s). Choix mémorisé." }.show()
            }
        } catch(e:Exception){ runOnUiThread { info.text="Impossible de scanner GitHub : ${e.message}" } }
    }.start()

    private fun refreshSelected() {
        if(selected.isEmpty()){ info.text="Sélectionne d'abord un ou plusieurs projets avec ⚙."; return }
        info.text="Vérification des changements…"; list.removeAllViews(); debug("Actualisation manuelle")
        Thread {
            selected.forEach { repo ->
                try {
                    val cached=loadSnapshot(repo)
                    val publicRunId=scrapeLatestRunId(repo)
                    if(cached!=null && publicRunId!=null && publicRunId==cached.optLong("runId",-1L)) { debug("$repo : cache utilisé")
                        runOnUiThread { renderSnapshot(cached, true) }
                    } else {
                        val fresh=scanRepo(repo)
                        saveSnapshot(repo,fresh); debug("$repo : nouvel état sauvegardé")
                        runOnUiThread { renderSnapshot(fresh, false) }
                    }
                } catch(e:Exception){
                    debug("$repo : ${e.message}")
                    val cached=loadSnapshot(repo)
                    runOnUiThread {
                        if(cached!=null) renderSnapshot(cached, true)
                        else addCard("🟡 $repo","Données indisponibles : ${e.message}")
                    }
                }
            }
            runOnUiThread { info.text="Actualisation terminée. Les projets inchangés ont été servis depuis la mémoire locale." }
        }.start()
    }

    private fun scanRepo(repo:String):JSONObject {
        val runs=getJsonArray("https://api.github.com/repos/greenpower2669/$repo/actions/runs?per_page=5","workflow_runs")
        val branches=getJsonArray("https://api.github.com/repos/greenpower2669/$repo/branches?per_page=10")
        val repoInfo=getJsonObject("https://api.github.com/repos/greenpower2669/$repo")
        val defaultBranch=repoInfo.optString("default_branch","main")
        val commits=getJsonArray("https://api.github.com/repos/greenpower2669/$repo/commits?per_page=1")
        val releases=getJsonArray("https://api.github.com/repos/greenpower2669/$repo/releases?per_page=1")
        val artifacts=getJsonArray("https://api.github.com/repos/greenpower2669/$repo/actions/artifacts?per_page=30","artifacts")
        val latestPush=commits.optJSONObject(0)?.optJSONObject("commit")?.optJSONObject("committer")?.optString("date").orEmpty()
        val latestRelease=releases.optJSONObject(0)?.optString("published_at").orEmpty()
        val latestArtifact=(0 until artifacts.length()).map { artifacts.getJSONObject(it) }
            .filter { !it.optBoolean("expired",true) && (it.optString("name").contains("apk",true) || it.optString("name").contains("aab",true)) }
            .maxByOrNull { it.optString("created_at") }
        var mergeNeeded=false
        (0 until branches.length()).map { branches.getJSONObject(it).optString("name") }
            .filter { it.isNotBlank() && it!=defaultBranch }.take(10).forEach { b ->
                try { if(getJsonObject("https://api.github.com/repos/greenpower2669/$repo/compare/$defaultBranch...$b").optInt("ahead_by",0)>0) mergeNeeded=true } catch(_:Exception){}
            }
        return JSONObject().apply {
            put("repo",repo); put("branchCount",branches.length())
            put("buildNeeded",isNewer(latestPush,latestArtifact?.optString("created_at").orEmpty()))
            put("mergeNeeded",mergeNeeded); put("releaseNeeded",isNewer(latestPush,latestRelease))
            put("artifactName",latestArtifact?.optString("name") ?: "")
            put("runId",runs.optJSONObject(0)?.optLong("id",-1L) ?: -1L)
            put("runs",runs); put("savedAt",System.currentTimeMillis())
        }
    }

    private fun renderSnapshot(s:JSONObject, fromCache:Boolean) {
        val repo=s.optString("repo")
        val runs=s.optJSONArray("runs") ?: JSONArray()
        val latest=(0 until minOf(5,runs.length())).map { runs.getJSONObject(it) }
        addRepo(repo,s.optInt("branchCount"),latest,s.optBoolean("buildNeeded"),s.optBoolean("mergeNeeded"),s.optBoolean("releaseNeeded"),s.optString("artifactName").ifBlank { null })
        if(fromCache) {
            val age=((System.currentTimeMillis()-s.optLong("savedAt",System.currentTimeMillis()))/60000L).coerceAtLeast(0)
            addCacheNote(repo,age)
        }
    }

    private fun addCacheNote(repo:String, age:Long) {
        val note=TextView(this).apply { text="💾 $repo : état local confirmé, inchangé · ${age} min"; textSize=14f; setPadding(18,4,18,10) }
        list.addView(note)
    }

    private fun saveSnapshot(repo:String,s:JSONObject){ cache.edit().putString("snapshot_$repo",s.toString()).apply() }
    private fun loadSnapshot(repo:String):JSONObject? = try { cache.getString("snapshot_$repo",null)?.let { JSONObject(it) } } catch(_:Exception){ null }

    private fun scrapeLatestRunId(repo:String):Long? {
        val c=(URL("https://github.com/greenpower2669/$repo/actions").openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent","DashbordGit/0.2 Android")
            setRequestProperty("Accept","text/html")
            connectTimeout=10000; readTimeout=15000
        }
        if(c.responseCode !in 200..299) return null
        val html=c.inputStream.bufferedReader().use { it.readText() }
        val m=Pattern.compile("/greenpower2669/"+Pattern.quote(repo)+"/actions/runs/(\\d+)").matcher(html)
        return if(m.find()) m.group(1)?.toLongOrNull() else null
    }

    private fun addRepo(repo:String, branchCount:Int, runs:List<JSONObject>, buildNeeded:Boolean, mergeNeeded:Boolean, releaseNeeded:Boolean, artifactName:String?) {
        val projectBox=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(18,18,18,18)
            background=android.graphics.drawable.GradientDrawable().apply { setColor(0x18FFFFFF); setStroke(3,0xFF777777.toInt()); cornerRadius=22f }
        }
        val oldList=list; list=projectBox
        projectBox.addView(TextView(this).apply { text="📦 "+repo.uppercase(); textSize=25f; gravity=Gravity.CENTER; setPadding(12,16,12,16) })
        val actions=mutableListOf<String>()
        if(buildNeeded) actions.add("APK/AAB à reconstruire")
        if(mergeNeeded) actions.add("merge à faire")
        if(releaseNeeded) actions.add("release à faire")
        val follow=if(actions.isEmpty()) "🟢 À jour" else "🟡 " + actions.joinToString(" • ")
        addCard("📦 $repo","Branches visibles : $branchCount\n$follow" + (artifactName?.let{"\nDernier paquet : $it"}?:""), voiceSummary(repo,actions))
        runs.forEach { r ->
            val status=r.optString("status")
            val conclusion=r.optString("conclusion")
            val dot=when { status!="completed"->"🟡"; conclusion=="success"->"🟢"; else->"🔴" }
            val msg=r.optJSONObject("head_commit")?.optString("message")?.lineSequence()?.firstOrNull() ?: "Sans commentaire de commit"
            addCard("$dot ${r.optString("name","GitHub Action")}","Branche : ${r.optString("head_branch")}\n$msg")
        }
        list=oldList
        oldList.addView(projectBox, LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,12,0,18) })
    }

    private fun addCard(head:String, body:String, voice:String?=null) {
        val box=TextView(this).apply {
            text="$head\n$body"; textSize=20f; setPadding(24,24,24,24); gravity=Gravity.START
            setOnClickListener { speak(voice ?: text.toString()) }
        }
        list.addView(box, LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,8,0,8) })
    }

    private fun voiceSummary(repo:String, actions:List<String>):String {
        val name=repo.replace(Regex("([a-z])([A-Z])"),"$1 $2")
        if(actions.isEmpty()) return "$name est à jour. La compilation, le merge et la release ne demandent pas d'action."
        val natural=actions.map { when(it) {
            "APK/AAB à reconstruire" -> "une nouvelle compilation Android est à faire"
            "merge à faire" -> "il reste un merge à faire"
            "release à faire" -> "il reste une release à faire"
            else -> it
        } }
        return "$name. " + natural.joinToString(". ") + "."
    }
    private fun isNewer(a:String,b:String)=if(a.isBlank()) false else if(b.isBlank()) true else try { Instant.parse(a).isAfter(Instant.parse(b)) } catch(_:Exception){ false }
    private fun getJsonObject(url:String):JSONObject {
        val conn=(URL(url).openConnection() as HttpURLConnection).apply { setRequestProperty("Accept","application/vnd.github+json"); setRequestProperty("User-Agent","DashbordGit-Android"); setRequestProperty("X-GitHub-Api-Version","2022-11-28"); connectTimeout=10000; readTimeout=15000 }
        if(conn.responseCode !in 200..299) throw Exception("GitHub HTTP "+conn.responseCode)
        return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
    }


    private fun chooseVoice() {
        val engines=tts?.engines ?: emptyList()
        val labels=listOf("Voix Android française (secours)")+engines.map { it.label+" — "+it.name }
        android.app.AlertDialog.Builder(this).setTitle("Moteur vocal installé")
            .setItems(labels.toTypedArray()) { _, index ->
                val engine=if(index==0) "" else engines[index-1].name
                prefs.edit().putString("tts_engine",engine).apply()
                tts?.shutdown()
                tts=if(engine.isBlank()) TextToSpeech(this,this) else TextToSpeech(this,this,engine)
                debug("Moteur vocal sélectionné : "+engine)
                info.text="Moteur choisi. Touchez une carte pour écouter."
            }.show()
    }
    private fun exportFile(code:Int) {
        val name=if(code==101) "DashbordGit-sauvegarde.json" else "DashbordGit-journal-debug.txt"
        val mime=if(code==101) "application/json" else "text/plain"
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type=mime; putExtra(Intent.EXTRA_TITLE,name)
        },code)
    }
    private fun importFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type="application/json"
        },103)
    }
    @Deprecated("Document picker")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK) return
        val uri=data?.data ?: return
        try {
            when(requestCode) {
                101 -> {
                    val snapshots=JSONObject()
                    selected.forEach { name -> loadSnapshot(name)?.let { snapshots.put(name,it) } }
                    val backup=JSONObject().put("format","dashboardgit-backup-v1")
                        .put("selected",JSONArray(selected.toList())).put("snapshots",snapshots)
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup.toString(2)) }
                    debug("Sauvegarde exportée")
                    info.text="Sauvegarde exportée."
                }
                102 -> {
                    val logs=synchronized(debugEvents) { debugEvents.joinToString("\n") }
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write("DashbordGit journal local\n"+logs) }
                    info.text="Journal de debug exporté."
                }
                103 -> {
                    val raw=contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Fichier illisible")
                    val backup=JSONObject(raw)
                    require(backup.optString("format")=="dashboardgit-backup-v1") { "Format inconnu" }
                    val array=backup.getJSONArray("selected")
                    val names=(0 until array.length()).map { array.getString(it) }
                    require(names.all { it.matches(Regex("[A-Za-z0-9_.-]{1,100}")) }) { "Nom de projet invalide" }
                    val snapshots=backup.getJSONObject("snapshots")
                    val editor=cache.edit().clear()
                    names.forEach { name -> snapshots.optJSONObject(name)?.let { editor.putString("snapshot_$name",it.toString()) } }
                    editor.apply()
                    selected.clear(); selected.addAll(names)
                    prefs.edit().putStringSet("selected_repos",selected.toSet()).apply()
                    list.removeAllViews()
                    selected.mapNotNull { loadSnapshot(it) }.sortedByDescending { it.optLong("savedAt") }.forEach { renderSnapshot(it,true) }
                    debug("Sauvegarde importée : ${selected.size} projets")
                    info.text="Sauvegarde restaurée : ${selected.size} projets."
                }
            }
        } catch(e:Exception) { debug("Import/export : ${e.message}"); info.text="Erreur : ${e.message}" }
    }

    private fun speak(s:String){ tts?.speak(s,TextToSpeech.QUEUE_FLUSH,null,"card") }
    override fun onInit(status:Int){ if(status==TextToSpeech.SUCCESS) tts?.language=Locale.FRANCE }
    override fun onDestroy(){ tts?.shutdown(); super.onDestroy() }

    private fun getJsonArray(url:String,key:String?=null):JSONArray {
        val c=(URL(url).openConnection() as HttpURLConnection).apply { setRequestProperty("Accept","application/vnd.github+json"); setRequestProperty("User-Agent","DashbordGit-Android"); setRequestProperty("X-GitHub-Api-Version","2022-11-28"); connectTimeout=10000; readTimeout=15000 }
        if(c.responseCode !in 200..299) throw Exception("GitHub HTTP ${c.responseCode}")
        val text=c.inputStream.bufferedReader().use { it.readText() }
        return if(key==null) JSONArray(text) else org.json.JSONObject(text).getJSONArray(key)
    }
}
