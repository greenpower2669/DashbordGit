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

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var list: LinearLayout
    private lateinit var info: TextView
    private var tts: TextToSpeech? = null
    private val selected = linkedSetOf<String>()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        tts = TextToSpeech(this, this)
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
                    .setPositiveButton("OK"){_,_-> info.text="${selected.size} projet(s) sélectionné(s)." }.show()
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
                    runOnUiThread { addRepo(repo,branches.length(),latest) }
                } catch(e:Exception){ runOnUiThread { addCard("🟡 $repo","Données indisponibles : ${e.message}") } }
            }
            runOnUiThread { info.text="Rafraîchissement terminé. Touchez une carte pour l'entendre." }
        }.start()
    }

    private fun addRepo(repo:String, branchCount:Int, runs:List<org.json.JSONObject>) {
        addCard("📦 $repo","Branches visibles : $branchCount")
        runs.forEach { r ->
            val status=r.optString("status")
            val conclusion=r.optString("conclusion")
            val dot=when { status!="completed"->"🟡"; conclusion=="success"->"🟢"; else->"🔴" }
            val msg=r.optJSONObject("head_commit")?.optString("message")?.lineSequence()?.firstOrNull() ?: "Sans commentaire de commit"
            addCard("$dot ${r.optString("name","GitHub Action")}","Branche : ${r.optString("head_branch")}\n$msg")
        }
    }

    private fun addCard(head:String, body:String) {
        val box=TextView(this).apply {
            text="$head\n$body"; textSize=20f; setPadding(24,24,24,24); gravity=Gravity.START
            setOnClickListener { speak(text.toString()) }
        }
        list.addView(box, LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,8,0,8) })
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
