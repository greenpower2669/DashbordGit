package fr.fab.dashboardgit

import android.os.Bundle
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

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var list: LinearLayout
    private lateinit var info: TextView
    private var tts: TextToSpeech? = null
    private val selected = linkedSetOf<String>()
    private val prefs by lazy { getSharedPreferences("dashboardgit", MODE_PRIVATE) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        tts = TextToSpeech(this, this)
        selected.addAll(prefs.getStringSet("selected_repos", emptySet()) ?: emptySet())
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,28,28,28) }
        val title=TextView(this).apply { text="● DashbordGit"; textSize=30f }
        val controls=LinearLayout(this)
        val settings=Button(this).apply { text="⚙ Projets"; setOnClickListener { chooseProjects() } }
        val refresh=Button(this).apply { text="↻ Rafraîchir"; setOnClickListener { refreshSelected() } }
        controls.addView(settings); controls.addView(refresh)
        info=TextView(this).apply { text="Choisis les projets avec ⚙ puis appuie sur Rafraîchir."; textSize=18f; setPadding(0,20,0,20) }
        val scroll=ScrollView(this)
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(title); root.addView(controls); root.addView(info); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
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
        info.text="Lecture GitHub en cours…"; list.removeAllViews()
        Thread {
            selected.forEach { repo ->
                try {
                    val runs=getJsonArray("https://api.github.com/repos/greenpower2669/$repo/actions/runs?per_page=10","workflow_runs")
                    val branches=getJsonArray("https://api.github.com/repos/greenpower2669/$repo/branches?per_page=10")
                    val latest=(0 until minOf(5,runs.length())).map { runs.getJSONObject(it) }
                    val repoInfo=getJsonObject("https://api.github.com/repos/greenpower2669/$repo")
                    val defaultBranch=repoInfo.optString("default_branch","main")
                    val commits=getJsonArray("https://api.github.com/repos/greenpower2669/$repo/commits?per_page=1")
                    val releases=getJsonArray("https://api.github.com/repos/greenpower2669/$repo/releases?per_page=1")
                    val artifacts=getJsonArray("https://api.github.com/repos/greenpower2669/$repo/actions/artifacts?per_page=30","artifacts")
                    val latestPush=commits.optJSONObject(0)?.optJSONObject("commit")?.optJSONObject("committer")?.optString("date").orEmpty()
                    val latestRelease=releases.optJSONObject(0)?.optString("published_at").orEmpty()
                    val latestArtifact=(0 until artifacts.length()).map { artifacts.getJSONObject(it) }.filter { !it.optBoolean("expired",true) && (it.optString("name").contains("apk",true) || it.optString("name").contains("aab",true)) }.maxByOrNull { it.optString("created_at") }
                    var mergeNeeded=false
                    (0 until branches.length()).map { branches.getJSONObject(it).optString("name") }.filter { it.isNotBlank() && it!=defaultBranch }.take(10).forEach { b ->
                        try { if(getJsonObject("https://api.github.com/repos/greenpower2669/$repo/compare/$defaultBranch...$b").optInt("ahead_by",0)>0) mergeNeeded=true } catch(_:Exception){}
                    }
                    runOnUiThread { addRepo(repo,branches.length(),latest,isNewer(latestPush,latestArtifact?.optString("created_at").orEmpty()),mergeNeeded,isNewer(latestPush,latestRelease),latestArtifact?.optString("name")) }
                } catch(e:Exception){ runOnUiThread { addCard("🟡 $repo","Données indisponibles : ${e.message}") } }
            }
            runOnUiThread { info.text="Rafraîchissement terminé. Touchez une carte pour l'entendre." }
        }.start()
    }

    private fun addRepo(repo:String, branchCount:Int, runs:List<JSONObject>, buildNeeded:Boolean, mergeNeeded:Boolean, releaseNeeded:Boolean, artifactName:String?) {
        val projectBox=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(18,18,18,18)
            background=android.graphics.drawable.GradientDrawable().apply { setColor(0x181FFFFFF); setStroke(3,0xFF777777.toInt()); cornerRadius=22f }
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
        val conn=(URL(url).openConnection() as HttpURLConnection).apply { setRequestProperty("Accept","application/vnd.github+json"); connectTimeout=10000; readTimeout=15000 }
        if(conn.responseCode !in 200..299) throw Exception("GitHub HTTP "+conn.responseCode)
        return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
    }

    private fun speak(s:String){ tts?.speak(s,TextToSpeech.QUEUE_FLUSH,null,"card") }
    override fun onInit(status:Int){ if(status==TextToSpeech.SUCCESS) tts?.language=Locale.FRANCE }
    override fun onDestroy(){ tts?.shutdown(); super.onDestroy() }

    private fun getJsonArray(url:String,key:String?=null):JSONArray {
        val c=(URL(url).openConnection() as HttpURLConnection).apply { setRequestProperty("Accept","application/vnd.github+json"); connectTimeout=10000; readTimeout=15000 }
        if(c.responseCode !in 200..299) throw Exception("GitHub HTTP ${c.responseCode}")
        val text=c.inputStream.bufferedReader().use { it.readText() }
        return if(key==null) JSONArray(text) else org.json.JSONObject(text).getJSONArray(key)
    }
}
