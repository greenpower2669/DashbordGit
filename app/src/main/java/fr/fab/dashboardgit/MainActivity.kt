package fr.fab.dashboardgit

import android.os.Bundle
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.io.File
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import android.net.Uri
import android.graphics.Typeface

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var list: LinearLayout
    private lateinit var info: TextView
    private var tts: TextToSpeech? = null
    private var supertonic: Supertonic3Speech? = null
    private lateinit var quotaLabel: TextView
    private lateinit var quotaProgress: ProgressBar
    private val webSource by lazy { GithubWebSource { debug(it) } }
    private val selected = linkedSetOf<String>()
    private val debugEvents=mutableListOf<String>()
    private fun debug(msg:String) {
        val safe=msg.replace(Regex("(?i)(authorization|bearer|token)\\s*[:=]\\s*\\S+"), "[SECRET]")
        val line=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.FRANCE).format(Date())+" | "+safe.take(500)
        synchronized(debugEvents) {
            debugEvents.add(line)
            if(debugEvents.size>500) debugEvents.removeAt(0)
        }
        runCatching {
            val file=File(filesDir,"dashboardgit-debug.log")
            if(file.exists() && file.length()>1_500_000) {
                file.writeText(file.readLines().takeLast(800).joinToString("\n", postfix="\n"))
            }
            file.appendText(line+"\n")
        }
    }
    private val prefs by lazy { getSharedPreferences("dashboardgit", MODE_PRIVATE) }
    private val cache by lazy { getSharedPreferences("dashboardgit_cache", MODE_PRIVATE) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        // Une seule migration au premier lancement de la version Supertonic 3 seule.
        // Elle remplace les anciens choix conservés par l'APK trois moteurs.
        if(!prefs.getBoolean("supertonic_v4_migrated",false)) {
            prefs.edit()
                .putInt("supertonic_sid",1)
                .putInt("supertonic_seed",2026)
                .putBoolean("speech_chunked",false)
                .putInt("speech_max_words",18)
                .putBoolean("supertonic_v4_migrated",true)
                .apply()
        }
        tts=TextToSpeech(this,this)
        supertonic=Supertonic3Speech(this) { debug(it) }
        selected.addAll(prefs.getStringSet("selected_repos",emptySet()) ?: emptySet())
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(24,24,24,18)
        }
        val title=TextView(this).apply {
            text="● DashbordGit"
            textSize=30f
            setTypeface(null,Typeface.BOLD)
            setPadding(0,0,0,12)
        }
        val controls=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val settings=Button(this).apply {
            text="⚙ PARAMÈTRES"
            textSize=17f
            setOnClickListener { showSettings() }
        }
        val refresh=Button(this).apply {
            text="🔄 ACTUALISER"
            textSize=17f
            setOnClickListener { refreshSelected() }
        }
        controls.addView(settings,LinearLayout.LayoutParams(0,-2,1f))
        controls.addView(refresh,LinearLayout.LayoutParams(0,-2,1f))
        quotaLabel=TextView(this).apply {
            textSize=19f
            setTypeface(null,Typeface.BOLD)
            setPadding(6,10,6,4)
        }
        quotaProgress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=100
            progress=0
            contentDescription="Jauge du quota API GitHub"
        }
        info=TextView(this).apply {
            text="Aucune connexion automatique. Appuie sur ACTUALISER pour lire les sources Web."
            textSize=19f
            setPadding(0,16,0,14)
        }
        val scroll=ScrollView(this)
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(title)
        root.addView(controls)
        root.addView(quotaLabel)
        root.addView(quotaProgress,LinearLayout.LayoutParams(-1,22))
        root.addView(info)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        showQuota()
        selected.mapNotNull { loadSnapshot(it) }
            .sortedByDescending { it.optLong("savedAt") }
            .forEach { renderSnapshot(it,true) }
        debug("Ouverture locale, "+selected.size+" projets (réseau inactif)")
    }

    private fun showSettings() {
        val choices=arrayOf(
            "📦 Choisir les projets",
            "🌐 Mode Web / API",
            "🔐 Autorisations par catégorie",
            "🎙 Supertonic 3 — voix et morcellement",
            "💾 Sauvegarde et journal",
            "📊 Vérifier le quota API"
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("⚙ Paramètres DashbordGit")
            .setItems(choices) { _,position ->
                when(position) {
                    0 -> chooseProjects()
                    1 -> chooseApiMode()
                    2 -> chooseApiCategories()
                    3 -> chooseVoice()
                    4 -> showFilesMenu()
                    5 -> verifyQuota()
                }
            }
            .setNegativeButton("FERMER",null)
            .show()
    }

    private fun showFilesMenu() {
        val choices=arrayOf("EXPORTER SAUVEGARDE","IMPORTER SAUVEGARDE","EXPORTER JOURNAL DEBUG")
        android.app.AlertDialog.Builder(this)
            .setTitle("Sauvegardes et diagnostic")
            .setItems(choices) { _,i ->
                when(i) { 0->exportFile(101); 1->importFile(); 2->exportFile(102) }
            }
            .setNegativeButton("FERMER",null)
            .show()
    }

    private fun chooseApiMode() {
        val labels=arrayOf(
            "Web uniquement — aucune API (par défaut)",
            "Me demander avant chaque nouvelle catégorie API",
            "Utiliser uniquement les catégories déjà autorisées"
        )
        val ids=arrayOf("WEB","ASK","ALLOWED")
        val active=ids.indexOf(prefs.getString("api_mode","WEB")).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle("Mode d'accès à GitHub")
            .setSingleChoiceItems(labels,active) { dialog,index ->
                prefs.edit().putString("api_mode",ids[index]).apply()
                info.text="Mode choisi : "+labels[index]
                debug("Réglage API : "+ids[index])
                dialog.dismiss()
            }
            .setNegativeButton("ANNULER",null).show()
    }

    private fun chooseApiCategories() {
        val keys=arrayOf("discover","runs","quota")
        val labels=arrayOf(
            "Découvrir des dépôts publics via API",
            "Lire les derniers builds via API",
            "Lire le quota via API"
        )
        val checked=BooleanArray(keys.size) { prefs.getBoolean("allow_api_"+keys[it],false) }
        android.app.AlertDialog.Builder(this)
            .setTitle("Autorisations API persistantes")
            .setMultiChoiceItems(labels,checked) { _,index,on ->
                prefs.edit().putBoolean("allow_api_"+keys[index],on).apply()
                debug("Permission "+keys[index]+": "+on)
            }
            .setPositiveButton("OK",null)
            .show()
    }

    // Must be called only from a background thread.
    private fun permitApi(category:String,why:String,estimated:Int=1):Boolean {
        val mode=prefs.getString("api_mode","WEB") ?: "WEB"
        if(mode=="WEB") return false
        if(prefs.getBoolean("allow_api_"+category,false)) return true
        if(mode!="ASK") return false
        val latch=CountDownLatch(1)
        val decision=AtomicBoolean(false)
        runOnUiThread {
            android.app.AlertDialog.Builder(this)
                .setTitle("Autoriser une requête API ?")
                .setMessage(why+"\nCoût estimé : "+estimated+" requête(s).")
                .setItems(arrayOf("AUTORISER UNE FOIS","TOUJOURS AUTORISER CETTE CATÉGORIE","REFUSER")) { _,index ->
                    if(index<2) decision.set(true)
                    if(index==1) prefs.edit().putBoolean("allow_api_"+category,true).apply()
                    debug("Consentement "+category+": "+index)
                    latch.countDown()
                }
                .setOnCancelListener { latch.countDown() }
                .show()
        }
        latch.await(90,TimeUnit.SECONDS)
        return decision.get()
    }

    private fun showQuota() {
        val limit=prefs.getInt("api_quota_limit",-1)
        val remaining=prefs.getInt("api_quota_remaining",-1)
        val measured=prefs.getLong("api_quota_time",0L)
        val reset=prefs.getLong("api_quota_reset",0L)
        if(limit<=0 || remaining<0 || measured<=0L) {
            quotaLabel.text="⚪ API : quota inconnu — 0 appel automatique"
            quotaProgress.progress=0
            return
        }
        val date=SimpleDateFormat("dd/MM HH:mm",Locale.FRANCE).format(Date(measured))
        val resetText=if(reset>0) SimpleDateFormat("HH:mm",Locale.FRANCE).format(Date(reset*1000L)) else "inconnue"
        quotaLabel.text="API : "+remaining+" / "+limit+" · mesuré "+date+" · réinitialisation "+resetText
        quotaProgress.max=limit
        quotaProgress.progress=remaining.coerceIn(0,limit)
    }

    private fun readApi(url:String):JSONObject {
        val connection=(URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept","application/vnd.github+json")
            setRequestProperty("User-Agent","DashbordGit-Android")
            setRequestProperty("X-GitHub-Api-Version","2022-11-28")
            connectTimeout=10000
            readTimeout=15000
        }
        try {
            val code=connection.responseCode
            val remaining=connection.getHeaderField("X-RateLimit-Remaining")?.toIntOrNull()
            val limit=connection.getHeaderField("X-RateLimit-Limit")?.toIntOrNull()
            val reset=connection.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()
            if(remaining!=null && limit!=null && limit>0) {
                prefs.edit().putInt("api_quota_limit",limit)
                    .putInt("api_quota_remaining",remaining)
                    .putLong("api_quota_time",System.currentTimeMillis())
                    .putLong("api_quota_reset",reset ?: 0L).apply()
                runOnUiThread { showQuota() }
            }
            if(code !in 200..299) error("GitHub API HTTP "+code)
            debug("API : "+URL(url).path+" (HTTP "+code+")")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }

    private fun verifyQuota() {
        Thread {
            if(!permitApi("quota","Vérifier le quota restant auprès de GitHub")) {
                runOnUiThread { info.text="Lecture du quota refusée ou désactivée. Dernière mesure conservée." }
                return@Thread
            }
            try {
                val json=readApi("https://api.github.com/rate_limit")
                val core=json.optJSONObject("resources")?.optJSONObject("core")
                if(core!=null) {
                    prefs.edit().putInt("api_quota_limit",core.optInt("limit",-1))
                        .putInt("api_quota_remaining",core.optInt("remaining",-1))
                        .putLong("api_quota_time",System.currentTimeMillis())
                        .putLong("api_quota_reset",core.optLong("reset",0L)).apply()
                }
                runOnUiThread { showQuota(); info.text="Quota GitHub vérifié à ta demande." }
            } catch(e:Exception) {
                debug("Quota : "+e.message)
                runOnUiThread { info.text="Impossible de vérifier le quota : "+e.message }
            }
        }.start()
    }


    private fun chooseProjects() {
        info.text="Lecture Web de la liste des projets publics…"
        Thread {
            val found=linkedSetOf<String>()
            found.addAll(selected)
            found.addAll(prefs.getStringSet("known_repos",emptySet()) ?: emptySet())
            runCatching { webSource.listPublicRepos() }
                .onSuccess { found.addAll(it) }
                .onFailure { debug("WEB projets : "+it.message) }
            if(found.size<=selected.size && permitApi("discover","Compléter la liste des dépôts publics absents du Web")) {
                try {
                    val url="https://api.github.com/users/greenpower2669/repos?per_page=100&sort=updated"
                    val json=readApiArray(url)
                    for(i in 0 until json.length()) {
                        val name=json.optJSONObject(i)?.optString("name").orEmpty()
                        if(name.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) found.add(name)
                    }
                } catch(e:Exception) { debug("API projets : "+e.message) }
            }
            prefs.edit().putStringSet("known_repos",found.toSet()).apply()
            runOnUiThread {
                if(found.isEmpty()) {
                    info.text="Liste Web indisponible. Passe en mode API autorisée pour la compléter."
                    return@runOnUiThread
                }
                val names=found.toList().sortedBy { it.lowercase() }
                val checked=BooleanArray(names.size) { selected.contains(names[it]) }
                android.app.AlertDialog.Builder(this)
                    .setTitle("Choisir les projets ("+names.size+")")
                    .setMultiChoiceItems(names.toTypedArray(),checked) { _,index,on ->
                        if(on) selected.add(names[index]) else selected.remove(names[index])
                    }
                    .setPositiveButton("ENREGISTRER") { _,_ ->
                        prefs.edit().putStringSet("selected_repos",selected.toSet()).apply()
                        info.text=selected.size.toString()+" projets sélectionnés. Choix mémorisé."
                    }
                    .setNegativeButton("ANNULER",null)
                    .show()
            }
        }.start()
    }

    private fun readApiArray(url:String):JSONArray {
        val connection=(URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept","application/vnd.github+json")
            setRequestProperty("User-Agent","DashbordGit-Android")
            setRequestProperty("X-GitHub-Api-Version","2022-11-28")
            connectTimeout=10000
            readTimeout=15000
        }
        try {
            val code=connection.responseCode
            val remaining=connection.getHeaderField("X-RateLimit-Remaining")?.toIntOrNull()
            val limit=connection.getHeaderField("X-RateLimit-Limit")?.toIntOrNull()
            if(remaining!=null && limit!=null && limit>0) {
                prefs.edit().putInt("api_quota_limit",limit)
                    .putInt("api_quota_remaining",remaining)
                    .putLong("api_quota_time",System.currentTimeMillis())
                    .putLong("api_quota_reset",connection.getHeaderField("X-RateLimit-Reset")?.toLongOrNull() ?: 0L).apply()
                runOnUiThread { showQuota() }
            }
            if(code !in 200..299) error("GitHub API HTTP "+code)
            debug("API projet(s) consultée à ta demande")
            return JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }


    private fun refreshSelected() {
        if(selected.isEmpty()) {
            info.text="Sélectionne d'abord des projets dans ⚙ PARAMÈTRES."
            return
        }
        info.text="Lecture Web en cours…"
        list.removeAllViews()
        debug("ACTUALISER : "+selected.size+" projets, réseau Web sur action utilisateur")
        Thread {
            var failures=0
            selected.forEach { repo ->
                try {
                    val snapshot=webSource.inspect(repo)
                    val runs=snapshot.optJSONArray("runs")
                    val latest=runs?.optJSONObject(0)
                    val jsonDate=snapshot.optString("jsonUpdatedAt","")
                    val jsonAge=try {
                        System.currentTimeMillis()-Instant.parse(jsonDate).toEpochMilli()
                    } catch(_:Exception) { Long.MAX_VALUE }
                    val jsonStale=snapshot.optString("source")=="JSON" && jsonAge>5*60*1000L
                    val incomplete=latest==null || latest.optString("status")=="unknown" || jsonStale
                    if(incomplete && permitApi("runs","Confirmer le dernier build du dépôt "+repo)) {
                        try {
                            val response=readApi("https://api.github.com/repos/greenpower2669/"+repo+"/actions/runs?per_page=5")
                            val apiRuns=response.optJSONArray("workflow_runs") ?: JSONArray()
                            if(apiRuns.length()>0) {
                                snapshot.put("runs",apiRuns).put("runId",apiRuns.optJSONObject(0)?.optLong("id",-1L) ?: -1L)
                                    .put("source","API")
                            }
                        } catch(e:Exception) { debug("API "+repo+" : "+e.message) }
                    }
                    saveSnapshot(repo,snapshot)
                    runOnUiThread { renderSnapshot(snapshot,false) }
                } catch(e:Exception) {
                    failures++
                    debug("Actualisation "+repo+" : "+e.message)
                    val cached=loadSnapshot(repo)
                    runOnUiThread {
                        if(cached!=null) renderSnapshot(cached,true)
                        else addCard("⚪ "+repo,"Sources indisponibles : état non vérifié.")
                    }
                }
            }
            runOnUiThread {
                info.text=if(failures==0) "Actualisation terminée. Les données non prouvées restent grisées."
                    else "Actualisation partielle ("+failures+" erreur(s)). Cache local conservé."
            }
        }.start()
    }



    private fun renderSnapshot(s:JSONObject,fromCache:Boolean) {
        val repo=s.optString("repo","Projet")
        val source=if(fromCache) "CACHE" else s.optString("source","WEB")
        val runs=s.optJSONArray("runs") ?: JSONArray()
        val latest=(0 until minOf(5,runs.length())).mapNotNull { runs.optJSONObject(it) }
        addRepo(s,latest,source)
        if(fromCache) {
            val age=((System.currentTimeMillis()-s.optLong("savedAt",System.currentTimeMillis()))/60000L).coerceAtLeast(0)
            list.addView(TextView(this).apply {
                text="💾 "+repo+" : dernière donnée locale, il y a "+age+" min. Non vérifiée aujourd'hui."
                textSize=16f
                setPadding(14,4,14,10)
            })
        }
    }

    private fun saveSnapshot(repo:String,s:JSONObject) {
        cache.edit().putString("snapshot_"+repo,s.toString()).apply()
    }
    private fun loadSnapshot(repo:String):JSONObject? =
        try { cache.getString("snapshot_"+repo,null)?.let { JSONObject(it) } } catch(_:Exception) { null }

    private fun runStatusText(run:JSONObject):Pair<String,String> {
        val status=run.optString("status","unknown")
        val conclusion=run.optString("conclusion","")
        return when {
            status=="queued" || status=="pending" || status=="waiting" || status=="requested" -> "🟠" to "EN ATTENTE"
            status=="in_progress" -> "🟡" to "EN COURS"
            status=="completed" && conclusion=="success" -> "🟢" to "RÉUSSI"
            status=="completed" && conclusion in setOf("failure","timed_out") -> "🔴" to "ÉCHEC"
            status=="completed" && conclusion=="cancelled" -> "⚪" to "ANNULÉ"
            status=="completed" && conclusion=="skipped" -> "⚪" to "IGNORÉ"
            else -> "⚪" to "NON VÉRIFIÉ"
        }
    }

    private fun addRepo(snapshot:JSONObject,runs:List<JSONObject>,source:String) {
        val repo=snapshot.optString("repo","?")
        val projectBox=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(18,18,18,18)
            background=android.graphics.drawable.GradientDrawable().apply {
                setColor(0x18FFFFFF); setStroke(3,0xFF777777.toInt()); cornerRadius=22f
            }
        }
        val oldList=list
        list=projectBox
        projectBox.addView(TextView(this).apply {
            text="📦 "+repo.uppercase()
            textSize=25f
            setTypeface(null,Typeface.BOLD)
            gravity=Gravity.CENTER
            setPadding(12,16,12,16)
        })
        val latest=runs.firstOrNull()
        val (dot,state)=if(latest!=null) runStatusText(latest) else "⚪" to "AUCUN BUILD VÉRIFIÉ"
        val displayedState=if(source=="JSON" || source=="CACHE") "DERNIER CONNU — "+state else state
        val actions=mutableListOf<String>()
        if(snapshot.optBoolean("buildKnown") && snapshot.optBoolean("buildNeeded")) actions.add("APK/AAB à reconstruire")
        if(snapshot.optBoolean("mergeKnown") && snapshot.optBoolean("mergeNeeded")) actions.add("merge à faire")
        if(snapshot.optBoolean("releaseKnown") && snapshot.optBoolean("releaseNeeded")) actions.add("release à faire")
        val confirmed=snapshot.optBoolean("buildKnown") &&
            snapshot.optBoolean("mergeKnown") && snapshot.optBoolean("releaseKnown")
        val follow=when {
            actions.isNotEmpty() -> "🟡 Actions : "+actions.joinToString(" • ")
            confirmed -> "🟢 Aucune action détectée dans les vérifications complètes"
            else -> "⚪ Compilation / merge / release : vérification incomplète"
        }
        val count=snapshot.optInt("branchCount",-1)
        val branchList=snapshot.optJSONArray("webBranches")
        val branchNames=if(branchList==null) "" else (0 until minOf(5,branchList.length()))
            .mapNotNull { branchList.optString(it).takeIf(String::isNotBlank) }.joinToString(", ")
        val branches=when {
            count<0 -> "Branches : non vérifiées"
            branchNames.isNotBlank() -> "Branches repérées : "+count+" (HTML partiel)\n"+branchNames
            else -> "Branches visibles : "+count
        }
        val age=if(snapshot.has("jsonUpdatedAt")) " · JSON "+snapshot.optString("jsonUpdatedAt") else ""
        val artifact=snapshot.optString("artifactName","")
        val artifactText=if(artifact.isNotBlank()) "\nDernier paquet : "+artifact else ""
        val body=branches+"\n"+follow+artifactText+"\nSource : "+source+age
        val spoken=repo+" : dernier build "+state+". "+
            if(actions.isEmpty()) "Autres opérations non confirmées." else actions.joinToString(". ")
        addCard(dot+" "+repo+" — "+displayedState,body,spoken)
        runs.forEach { run ->
            val (symbol,label)=runStatusText(run)
            val message=run.optJSONObject("head_commit")?.optString("message")?.lineSequence()?.firstOrNull()
                ?.ifBlank { "Message indisponible" } ?: "Message du commit indisponible"
            val branch=run.optString("head_branch").ifBlank { "non précisée" }
            val url=run.optString("html_url").ifBlank {
                val id=run.optLong("id",-1L)
                if(id>0) "https://github.com/greenpower2669/"+repo+"/actions/runs/"+id else ""
            }
            addCard(symbol+" "+label+" — "+run.optString("name","GitHub Actions"),
                "Branche : "+branch+"\n"+message+"\nAppui long : ouvrir GitHub",
                null,url)
        }
        if(runs.isEmpty()) addCard("⚪ ÉTAT INCONNU","Le Web n'a pas fourni de résultat fiable. Utilise l'API autorisée pour confirmer.")
        list=oldList
        oldList.addView(projectBox,LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,12,0,18) })
    }

    private fun addCard(head:String,body:String,voice:String?=null,url:String?=null) {
        val box=TextView(this).apply {
            text=head+"\n"+body
            textSize=20f
            setPadding(24,24,24,24)
            gravity=Gravity.START
            setOnClickListener { speak(voice ?: text.toString()) }
            if(!url.isNullOrBlank()) setOnLongClickListener {
                startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))
                true
            }
        }
        list.addView(box,LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,8,0,8) })
    }


    private fun chooseVoice() {
        val layout=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(32,12,32,12)
        }
        val label=TextView(this).apply { text="Voix Supertonic 3"; textSize=20f }
        val voices=Spinner(this)
        voices.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,
            (0..9).map { "Voix "+it+" (SID "+it+")" })
        voices.setSelection(prefs.getInt("supertonic_sid",1).coerceIn(0,9))
        val seedTitle=TextView(this).apply { text="Seed de génération (2026 par défaut)"; textSize=20f }
        val seed=EditText(this).apply {
            inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(prefs.getInt("supertonic_seed",2026).toString())
            textSize=23f
        }
        val split=CheckBox(this).apply {
            text="Morceler la parole en paquets de mots"
            isChecked=prefs.getBoolean("speech_chunked",false)
            textSize=20f
        }
        val maxLabel=TextView(this).apply { textSize=20f }
        val maxWords=SeekBar(this).apply {
            max=35
            progress=(prefs.getInt("speech_max_words",18).coerceIn(5,40)-5)
        }
        fun updateMax() {
            maxLabel.text="Longueur maximale : "+(maxWords.progress+5)+" mots"
            maxWords.isEnabled=split.isChecked
        }
        split.setOnCheckedChangeListener { _,_ -> updateMax() }
        maxWords.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b:SeekBar?,v:Int,fromUser:Boolean) { updateMax() }
            override fun onStartTrackingTouch(b:SeekBar?) {}
            override fun onStopTrackingTouch(b:SeekBar?) {}
        })
        updateMax()
        val customTitle=TextView(this).apply { text="Phrase de test"; textSize=20f }
        val phrase=EditText(this).apply {
            setText(prefs.getString("speech_test","Bonjour. Je teste la voix Supertonic trois et sa fluidité sur Android."))
            textSize=19f
            minLines=2
        }
        listOf(label,voices,seedTitle,seed,split,maxLabel,maxWords,customTitle,phrase).forEach { layout.addView(it) }
        val scroll=ScrollView(this).apply { addView(layout) }
        fun save():Boolean {
            val value=seed.text.toString().trim().toIntOrNull()
            if(value==null) {
                info.text="Seed invalide. Entre un nombre entier valide."
                return false
            }
            prefs.edit()
                .putInt("supertonic_sid",voices.selectedItemPosition)
                .putInt("supertonic_seed",value)
                .putBoolean("speech_chunked",split.isChecked)
                .putInt("speech_max_words",maxWords.progress+5)
                .putString("speech_test",phrase.text.toString().take(800))
                .apply()
            debug("Supertonic : SID "+voices.selectedItemPosition+
                ", seed "+value+", morcellement "+split.isChecked+
                ", max "+(maxWords.progress+5))
            return true
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("🎙 Supertonic 3 — voix et inférence")
            .setView(scroll)
            .setPositiveButton("ENREGISTRER") { _,_ -> if(save()) info.text="Paramètres vocaux enregistrés." }
            .setNeutralButton("TESTER") { _,_ -> if(save()) speak(phrase.text.toString()) }
            .setNegativeButton("ANNULER",null)
            .show()
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
                        .put("supertonicSid",prefs.getInt("supertonic_sid",1))
                        .put("supertonicSeed",prefs.getInt("supertonic_seed",2026))
                        .put("speechChunked",prefs.getBoolean("speech_chunked",false))
                        .put("speechMaxWords",prefs.getInt("speech_max_words",18))
                        .put("speechTest",prefs.getString("speech_test",""))
                        .put("apiMode",prefs.getString("api_mode","WEB"))
                        
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup.toString(2)) }
                    debug("Sauvegarde exportée")
                    info.text="Sauvegarde exportée."
                }
                102 -> {
                    val logFile=File(filesDir,"dashboardgit-debug.log")
                    val logs=if(logFile.exists()) logFile.readText() else synchronized(debugEvents) { debugEvents.joinToString("\n") }
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
                    prefs.edit()
                        .putInt("supertonic_sid",backup.optInt("supertonicSid",1).coerceIn(0,9))
                        .putInt("supertonic_seed",backup.optInt("supertonicSeed",2026))
                        .putBoolean("speech_chunked",backup.optBoolean("speechChunked",false))
                        .putInt("speech_max_words",backup.optInt("speechMaxWords",18).coerceIn(5,40))
                        .putString("speech_test",backup.optString("speechTest","").take(800))
                        .putString("api_mode",backup.optString("apiMode","WEB").let { if(it in setOf("WEB","ASK","ALLOWED")) it else "WEB" })
                        .apply()
                    list.removeAllViews()
                    selected.mapNotNull { loadSnapshot(it) }.sortedByDescending { it.optLong("savedAt") }.forEach { renderSnapshot(it,true) }
                    debug("Sauvegarde importée : ${selected.size} projets")
                    info.text="Sauvegarde restaurée : ${selected.size} projets."
                }
            }
        } catch(e:Exception) { debug("Import/export : ${e.message}"); info.text="Erreur : ${e.message}" }
    }

    private fun speak(s:String) {
        if(s.isBlank()) return
        val sid=prefs.getInt("supertonic_sid",1).coerceIn(0,9)
        val seed=prefs.getInt("supertonic_seed",2026)
        val chunked=prefs.getBoolean("speech_chunked",false)
        val maxWords=prefs.getInt("speech_max_words",18).coerceIn(5,40)
        val engine=supertonic
        if(engine==null) {
            tts?.speak(s,TextToSpeech.QUEUE_FLUSH,null,"card")
            return
        }
        engine.speak(s,sid,seed,chunked,maxWords) { error ->
            debug("Supertonic 3 indisponible, secours Android : "+error.message)
            runOnUiThread { tts?.speak(s,TextToSpeech.QUEUE_FLUSH,null,"card") }
        }
    }

    override fun onInit(status:Int) {
        if(status==TextToSpeech.SUCCESS) tts?.language=Locale.FRANCE
    }
    override fun onDestroy() {
        supertonic?.close()
        tts?.shutdown()
        super.onDestroy()
    }
}
