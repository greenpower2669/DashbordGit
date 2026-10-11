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
    private var homePreview:HomeExplorerPreview?=null
    private val projectSlots=linkedMapOf<String,LinearLayout>()
    private val currentSnapshots=linkedMapOf<String,JSONObject>()
    private lateinit var apiOffer:LinearLayout
    private var webScanComplete=false
    private var currentRefresh=0
    private var apiCheckInProgress=false
    private var apiOfferWasDismissed=false
    private var scannerErrors=0

    private val refreshInFlight=AtomicBoolean(false)
    private val webSource by lazy { GithubWebSource(this) { debug(it) } }
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
        val mosaicButton=Button(this).apply {
            text="🧩 MOSAÏQUE CHROMIUM"
            textSize=18f
            contentDescription="Ouvrir la mosaïque de pages GitHub sans chargement automatique"
            setOnClickListener {
                startActivity(Intent(this@MainActivity,MosaicActivity::class.java))
            }
        }
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
        apiOffer=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            visibility=android.view.View.GONE
            setPadding(14,12,14,12)
        }
        val scroll=ScrollView(this).apply {
            isFillViewport=false
            isVerticalScrollBarEnabled=true
        }
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(title)
        root.addView(controls)
        root.addView(mosaicButton)
        root.addView(quotaLabel)
        root.addView(quotaProgress,LinearLayout.LayoutParams(-1,22))
        homePreview=HomeExplorerPreview(
            this,prefs,{ debug(it) },
            { startActivity(Intent(this@MainActivity,MosaicActivity::class.java)) },
            { evidence -> onExplorerEvidence(evidence) },
            { if(webScanComplete) offerApiIfNeeded() }
        )
        root.addView(homePreview!!.view,LinearLayout.LayoutParams(-1,-2).apply {
            setMargins(0,12,0,10)
        })
        root.addView(info)
        root.addView(apiOffer)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        showQuota()
        initializeProjectCards(selected.toList())
        debug("Ouverture locale, "+selected.size+" projets (réseau inactif)")
    }

    /** Stable scrollable slots: existing cards never disappear during refresh. */
    private fun ensureProjectSlot(repo:String):LinearLayout {
        projectSlots[repo]?.let { return it }
        val holder=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            contentDescription="Projet "+repo
        }
        projectSlots[repo]=holder
        list.addView(holder,LinearLayout.LayoutParams(-1,-2))
        return holder
    }

    private fun initializeProjectCards(projects:List<String>) {
        if(projectSlots.keys.toSet()!=projects.toSet()) {
            // The selection changed in Settings; rebuild ONCE before scanning,
            // not midway through scrolling and never for each repo.
            list.removeAllViews()
            projectSlots.clear()
            currentSnapshots.keys.retainAll(projects.toSet())
        }
        for(repo in projects) {
            val holder=ensureProjectSlot(repo)
            if(!currentSnapshots.containsKey(repo)) {
                val saved=loadSnapshot(repo)
                if(saved!=null) {
                    displayProject(repo,saved,true)
                } else {
                    holder.removeAllViews()
                    holder.addView(TextView(this).apply {
                        text="📦 "+repo+"\n⚪ Pas encore de données. Appuie sur ACTUALISER."
                        textSize=21f
                        setPadding(16,22,16,22)
                    })
                }
            }
        }
    }

    private fun displayProject(repo:String,snapshot:JSONObject,fromCache:Boolean=false) {
        val holder=ensureProjectSlot(repo)
        // Entire replacement happens inside the same stable holder, keeping
        // all other project views and ScrollView position intact.
        currentSnapshots[repo]=JSONObject(snapshot.toString())
        holder.removeAllViews()
        val existingList=list
        list=holder
        try {
            renderSnapshot(snapshot,fromCache)
        } finally {
            list=existingList
        }
    }

    private fun refreshSummary() {
        var verified=0
        var unknown=0
        for(repo in selected) {
            val recent=currentSnapshots[repo]?.optJSONArray("runs")?.optJSONObject(0)
            if(GithubWorkflowStatus.classify(recent).verified) verified++ else unknown++
        }
        info.text="Tableau consultable : "+verified+" état(s) confirmé(s), "+
            unknown+" inconnu(s), "+scannerErrors+" erreur(s) Web."
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
            "Web uniquement — aucune API sans exception",
            "Me demander avant tout nouveau recours API (par défaut)",
            "Utiliser uniquement les catégories déjà autorisées"
        )
        val ids=arrayOf("WEB","ASK","ALLOWED")
        val active=ids.indexOf(prefs.getString("api_mode","ASK")).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle("Mode d'accès à GitHub")
            .setSingleChoiceItems(labels,active) { dialog,index ->
                prefs.edit().putString("api_mode",ids[index]).apply()
                info.text="Mode choisi : "+labels[index]
                debug("Réglage API : "+ids[index])
                dialog.dismiss()
                if(webScanComplete)offerApiIfNeeded()
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
        val mode=prefs.getString("api_mode","ASK")
        when(GithubApiConsentPolicy.decide(
            mode,prefs.getBoolean("allow_api_"+category,false)
        )) {
            GithubApiConsentPolicy.Decision.BLOCK -> return false
            GithubApiConsentPolicy.Decision.PERMIT -> return true
            GithubApiConsentPolicy.Decision.REQUEST -> { /* ask the user below */ }
        }
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
        if(!refreshInFlight.compareAndSet(false,true)) {
            info.text="Une actualisation Web est déjà en cours."
            return
        }
        val serial=++currentRefresh
        val projects=selected.toList()
        scannerErrors=0
        webScanComplete=false
        apiOfferWasDismissed=false
        apiOffer.visibility=android.view.View.GONE
        apiOffer.removeAllViews()
        initializeProjectCards(projects)
        // Previously verified snapshots stay on-screen during the entire
        // refresh. The browser's output has a separate evidence channel.
        homePreview?.start(projects)
        info.text="Lecture Web 0 / "+projects.size+
            " · fais défiler les cartes pendant l'actualisation."
        debug("ACTUALISER : "+projects.size+" projets, cartes conservées")
        Thread {
            try {
                for((index,repo) in projects.withIndex()) {
                    if(isFinishing || isDestroyed)break
                    val result=runCatching { webSource.inspect(repo) }
                    result.onFailure { e ->
                        debug("Web "+repo+" : "+e.javaClass.simpleName+" "+e.message)
                    }
                    runOnUiThread {
                        if(serial!=currentRefresh || isFinishing || isDestroyed)return@runOnUiThread
                        result.fold(onSuccess={ snapshot ->
                            // New DOM evidence can precede the HTML reader.
                            val evidence=domEvidence[repo]
                            val merged=if(evidence!=null)
                                GithubDomEvidence.merge(snapshot,evidence) else snapshot
                            displayProject(repo,merged,false)
                            saveSnapshot(repo,merged)
                        },onFailure={
                            scannerErrors++
                            val saved=currentSnapshots[repo]
                            if(saved!=null) {
                                displayProject(repo,saved,true)
                            }
                        })
                        info.text="Lecture Web "+(index+1)+"/"+projects.size+
                            " terminée · liste défilable pendant le chargement."
                    }
                }
            } catch(e:Exception) {
                debug("Actualisation interrompue : "+e.javaClass.simpleName+" "+e.message)
            } finally {
                refreshInFlight.set(false)
                runOnUiThread {
                    if(serial==currentRefresh && !isFinishing && !isDestroyed) {
                        webScanComplete=true
                        refreshSummary()
                        offerApiIfNeeded()
                    }
                }
            }
        }.start()
    }

    private val domEvidence=linkedMapOf<String,GithubDomEvidence>()

    private fun onExplorerEvidence(evidence:GithubDomEvidence) {
        if(isFinishing || isDestroyed || evidence.repo !in selected)return
        if(!evidence.verified) {
            debug("Chromium "+evidence.repo+" : statut non prouvé pour "+
                evidence.runId+" ("+evidence.reason.take(100)+")")
            return
        }
        val old=domEvidence[evidence.repo]
        if(old!=null && old.runId>evidence.runId)return
        domEvidence[evidence.repo]=evidence
        val snapshot=currentSnapshots[evidence.repo] ?: loadSnapshot(evidence.repo)
            ?: JSONObject().put("repo",evidence.repo).put("savedAt",0L)
                .put("runs",JSONArray())
        val merged=GithubDomEvidence.merge(snapshot,evidence)
        if(merged.optString("source")== "CHROMIUM_DOM") {
            displayProject(evidence.repo,merged,false)
            saveSnapshot(evidence.repo,merged)
            debug("Chromium "+evidence.repo+" #"+evidence.runId+
                " : "+evidence.status+"/"+evidence.conclusion+
                ", preuve "+evidence.reason.take(150))
            if(webScanComplete) {
                refreshSummary()
                offerApiIfNeeded()
            }
        }
    }

    private fun offerApiIfNeeded() {
        if(!webScanComplete || apiCheckInProgress || apiOfferWasDismissed)return
        // Chromium may still be traversing real pages. Await its finish;
        // offering REST earlier would waste quota for statuses it can prove.
        if(homePreview?.isExploring()==true) {
            info.append("\nChromium termine l'exploration, puis vérification des inconnus.")
            return
        }
        val unknown=selected.filter { repo ->
            !GithubWorkflowStatus.classify(
                currentSnapshots[repo]?.optJSONArray("runs")?.optJSONObject(0)
            ).verified
        }
        if(unknown.isEmpty()) {
            apiOffer.visibility=android.view.View.GONE
            apiOffer.removeAllViews()
            return
        }
        showApiChoice(unknown)
    }

    private fun showApiChoice(repos:List<String>) {
        apiOffer.removeAllViews()
        apiOffer.visibility=android.view.View.VISIBLE
        val mode=prefs.getString("api_mode","ASK")
        val blocked=mode=="WEB"
        apiOffer.background=android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFFF2F2F6.toInt())
            setStroke(2,0xFF777788.toInt())
            cornerRadius=18f
        }
        apiOffer.addView(TextView(this).apply {
            text="⚪ "+repos.size+" statut(s) restent inconnus : "+
                repos.joinToString(", ")+".\n"+
                if(blocked)
                    "Tu as choisi WEB UNIQUEMENT : aucun appel API ne sera fait."
                else
                    "Tu peux continuer sans API, ou autoriser "+
                    repos.size+" vérification(s) officielles à ta demande."
            textSize=18f
            setPadding(8,6,8,12)
        })
        val remember=CheckBox(this).apply {
            text="Mémoriser l'autorisation pour les prochaines fois"
            textSize=16f
            isChecked=false
            visibility=if(blocked) android.view.View.GONE else android.view.View.VISIBLE
        }
        if(!blocked)apiOffer.addView(remember)
        val actions=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
        }
        val refuse=Button(this).apply {
            text="🚫 CONTINUER SANS API"
            textSize=19f
            setOnClickListener {
                apiOfferWasDismissed=true
                apiOffer.visibility=android.view.View.GONE
                apiOffer.removeAllViews()
                debug("API : REFUS EXPLICITE pour "+repos.size+" projet(s), "+
                    "aucune requête REST")
                refreshSummary()
                info.append("\nAPI refusée. Les états non prouvés restent gris.")
            }
        }
        val accept=Button(this).apply {
            text=if(blocked) "⚙ ACTIVER LE MODE DEMANDE" else
                "✅ AUTORISER CETTE VÉRIFICATION"
            textSize=18f
            setOnClickListener {
                if(blocked) {
                    chooseApiMode()
                    return@setOnClickListener
                }
                if(apiCheckInProgress)return@setOnClickListener
                apiOfferWasDismissed=true
                apiOffer.visibility=android.view.View.GONE
                apiOffer.removeAllViews()
                if(remember.isChecked) {
                    prefs.edit().putBoolean("allow_api_runs",true).apply()
                    debug("API : autorisation durable explicite, révocable dans Paramètres")
                } else {
                    debug("API : autorisation unique explicite pour "+repos.size+" projet(s)")
                }
                confirmViaApi(repos.toList())
            }
        }
        actions.addView(refuse)
        actions.addView(accept)
        apiOffer.addView(actions)
    }

    private fun confirmViaApi(repositories:List<String>) {
        apiCheckInProgress=true
        val serial=currentRefresh
        Thread {
            var completed=0
            try {
                for(repo in repositories) {
                    if(isFinishing || isDestroyed || serial!=currentRefresh)break
                    try {
                        val response=readApi(
                            "https://api.github.com/repos/greenpower2669/"+
                            repo+"/actions/runs?per_page=5")
                        val runs=response.optJSONArray("workflow_runs") ?: JSONArray()
                        if(runs.length()==0)continue
                        runOnUiThread {
                            if(serial!=currentRefresh || isFinishing || isDestroyed)return@runOnUiThread
                            val prior=currentSnapshots[repo] ?: JSONObject().put("repo",repo)
                            val merged=JSONObject(prior.toString())
                                .put("runs",runs)
                                .put("runId",runs.optJSONObject(0)?.optLong("id",-1L) ?: -1L)
                                .put("source","API")
                                .put("savedAt",System.currentTimeMillis())
                                .put("needsStatusConfirmation",false)
                            displayProject(repo,merged,false)
                            saveSnapshot(repo,merged)
                            refreshSummary()
                        }
                        completed++
                    } catch(e:Exception) {
                        debug("API "+repo+" : "+e.javaClass.simpleName+" "+e.message)
                    }
                }
            } finally {
                runOnUiThread {
                    apiCheckInProgress=false
                    if(serial==currentRefresh && !isFinishing && !isDestroyed) {
                        refreshSummary()
                        info.append("\nAPI : "+completed+" projet(s) complété(s) sur "+
                            repositories.size+". Refus possible à chaque demande.")
                    }
                }
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
        val state=GithubWorkflowStatus.classify(run)
        return state.marker to state.label
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
        // A compact list is especially helpful on small screens with zoom.
        // Do not repeat five identical grey cards when the Web cannot prove status.
        val anyKnown=runs.any { runStatusText(it).first!="⚪" }
        val shown=if(anyKnown) runs.take(3) else runs.take(1)
        shown.forEach { run ->
            val (symbol,label)=runStatusText(run)
            val message=run.optJSONObject("head_commit")?.optString("message")
                ?.lineSequence()?.firstOrNull()?.ifBlank { "Message non disponible" }
                ?: "Message du commit non disponible"
            val branch=run.optString("head_branch").ifBlank { "non précisée" }
            val url=run.optString("html_url").ifBlank {
                val id=run.optLong("id",-1L)
                if(id>0) "https://github.com/greenpower2669/"+repo+"/actions/runs/"+id else ""
            }
            val shortId=run.optLong("id",-1L)
            val runName=run.optString("name","GitHub Actions")
            val title=symbol+" "+label+" — "+runName+
                if(shortId>0) " #"+shortId else ""
            addCard(title,"Branche : "+branch+"\n"+message+"\nAppui long : ouvrir GitHub",
                null,url)
        }
        if(shown.size<runs.size) {
            addCard("ℹ️ Historique condensé",
                (runs.size-shown.size).toString()+" autre(s) build(s) détecté(s). "+
                "Les résultats sans preuve restent inconnus.")
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
            val safeUrl=if(url.isNullOrBlank()) null else runCatching { Uri.parse(url) }.getOrNull()
                ?.takeIf { it.scheme=="https" && it.host=="github.com" }
            if(safeUrl!=null) setOnLongClickListener {
                startActivity(Intent(Intent.ACTION_VIEW,safeUrl))
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
                        .put("apiMode",prefs.getString("api_mode","ASK"))
                        
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
                        .putString("api_mode",backup.optString("apiMode",prefs.getString("api_mode","ASK") ?: "ASK").let { if(it in setOf("WEB","ASK","ALLOWED")) it else "ASK" })
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
    override fun onPause() {
        homePreview?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        homePreview?.onResume()
    }

    override fun onDestroy() {
        homePreview?.destroy()
        homePreview=null
        supertonic?.close()
        tts?.shutdown()
        super.onDestroy()
    }
}
