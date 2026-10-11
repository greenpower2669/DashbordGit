package fr.fab.dashboardgit

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * DASHGIT-CHROMIUM-001
 *
 * Native Android WebView is powered by the system Chromium WebView provider.
 * NOT an iframe, NOT an extra bundled browser and NOT a REST scraper.
 *
 * No page is loaded until a real user interaction (individual or global).
 * At most N live WebViews share the system provider. Evicted previews show
 * labeled FROZEN screenshots; they never masquerade as live status.
 */
class MosaicActivity : AppCompatActivity() {
    private val mainHandler=Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("dashboardgit",MODE_PRIVATE) }
    private val cache by lazy { getSharedPreferences("dashboardgit_cache",MODE_PRIVATE) }
    private val tiles=linkedMapOf<String,Tile>()
    private val active=LinkedHashMap<String,Tile>(8,0.75f,true)
    private lateinit var status:TextView
    private lateinit var allButton:Button
    private lateinit var tileContainer:LinearLayout
    private var liveLimit=3
    private var alive=true
    private var batchGeneration=0
    private var batchActive=false
    private var statsPageLoads=0
    private var statsResources=0
    private var statsBrowserApi=0
    private var systemSpeech:android.speech.tts.TextToSpeech?=null
    private var pendingSpeech:String?=null
    private var voiceInitialized=false

    private inner class Tile(
        val repo:String,
        var kind:GithubMosaicTargets.Kind,
        var url:String,
        val frame:LinearLayout,
        val preview:FrameLayout,
        val label:TextView,
        val detail:TextView,
        val counter:TextView,
        var web:WebView?=null,
        var frozen:Bitmap?=null,
        var wasLoaded:Boolean=false,
        var afterLoad:(()->Unit)?=null,
        var lastLoadStart:Long=0L,
        var domChanges:Int=0,
        var resourceCount:AtomicInteger=AtomicInteger(),
        var browserApiCount:AtomicInteger=AtomicInteger()
    )

    private fun dp(value:Int):Int=(resources.displayMetrics.density*value+0.5f).toInt()

    private fun log(line:String) {
        runCatching {
            val f=java.io.File(filesDir,"dashboardgit-debug.log")
            f.appendText(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.FRANCE).format(Date())+
                " | MOSAÏQUE "+line.replace(Regex("(?i)(token|bearer|authorization)\\s*[:=]\\s*\\S+"),"[MASQUÉ]")
                    .take(380)+"\n")
        }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        liveLimit=prefs.getInt("mosaic_live_limit",3).coerceIn(2,6)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(12),dp(10),dp(12),dp(12))
        }
        val heading=TextView(this).apply {
            text="🧩 MOSAÏQUE GITHUB"
            textSize=26f
            setTypeface(null,Typeface.BOLD)
        }
        val controls=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        allButton=Button(this).apply {
            text="🔄 TOUT RECHARGER"
            textSize=15f
            setOnClickListener { refreshAll() }
        }
        val capacity=Button(this).apply {
            text="⚙ FENÊTRES"
            textSize=15f
            setOnClickListener { chooseLimit() }
        }
        controls.addView(allButton,LinearLayout.LayoutParams(0,dp(58),2f))
        controls.addView(capacity,LinearLayout.LayoutParams(0,dp(58),1f))
        status=TextView(this).apply {
            textSize=17f
            setPadding(dp(5),dp(8),dp(5),dp(12))
            text="Aucun accès réseau au lancement. Touchez 🔄 pour charger."
        }
        val scroll=ScrollView(this)
        tileContainer=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
        }
        scroll.addView(tileContainer)
        root.addView(heading)
        root.addView(controls)
        root.addView(status)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)

        val selected=prefs.getStringSet("selected_repos",emptySet()).orEmpty()
            .filter { Regex("[A-Za-z0-9_.-]{1,100}").matches(it) }
            .sorted()
        if(selected.isEmpty()) {
            status.text="Aucun projet sélectionné. Reviens dans ⚙ PARAMÈTRES → projets."
            return
        }
        val cols=if(resources.configuration.screenWidthDp>=720) 2 else 1
        var row:LinearLayout?=null
        for((index,repo) in selected.withIndex()) {
            if(index%cols==0) {
                row=LinearLayout(this).apply {
                    orientation=LinearLayout.HORIZONTAL
                    weightSum=cols.toFloat()
                }
                tileContainer.addView(row,LinearLayout.LayoutParams(-1,-2))
            }
            val tile=createTile(repo)
            tiles[repo]=tile
            row?.addView(tile.frame,LinearLayout.LayoutParams(0,-2,1f).apply {
                setMargins(dp(4),dp(6),dp(4),dp(6))
            })
        }
        status.text=selected.size.toString()+" cartes disponibles. "+
            "Jusqu'à "+liveLimit+" fenêtres Chromium actives ; le reste est figé. "+
            "Chargement uniquement sur action utilisateur."
        log("Ouverture : "+selected.size+" cartes, 0 chargement réseau, maximum "+liveLimit+" WebView")
    }

    private fun cached(repo:String):JSONObject?=runCatching {
        cache.getString("snapshot_$repo",null)?.let { JSONObject(it) }
    }.getOrNull()

    private fun targetFor(repo:String,kind:GithubMosaicTargets.Kind):String {
        val snapshot=cached(repo)
        val run=snapshot?.optJSONArray("runs")?.optJSONObject(0)
        val id=run?.optLong("id",-1L)?.takeIf { it>0 }
            ?: snapshot?.optLong("runId",-1L)?.takeIf { it>0 }
        val sha=snapshot?.optString("latestCommit")
        return GithubMosaicTargets.target(repo,kind,id,sha)
    }

    private fun createTile(repo:String):Tile {
        val kind=GithubMosaicTargets.kind(prefs.getString("mosaic_target_$repo",null))
        val url=targetFor(repo,kind)
        val frame=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(10),dp(10),dp(10),dp(12))
            background=GradientDrawable().apply {
                setColor(0xFFF6F6F8.toInt())
                setStroke(dp(1),0xFF777780.toInt())
                cornerRadius=dp(14).toFloat()
            }
        }
        val header=TextView(this).apply {
            text="📦 "+repo.uppercase(Locale.ROOT)
            textSize=22f
            gravity=android.view.Gravity.CENTER
            setTypeface(null,Typeface.BOLD)
            setTextColor(Color.BLACK)
            setPadding(dp(2),dp(6),dp(2),dp(8))
        }
        val snapshot=cached(repo)
        val state=GithubWorkflowStatus.classify(snapshot?.optJSONArray("runs")?.optJSONObject(0))
        val savedAt=snapshot?.optLong("savedAt",0L) ?: 0L
        val since=if(savedAt>0)
            SimpleDateFormat("dd/MM HH:mm",Locale.FRANCE).format(Date(savedAt))
            else "jamais"
        val label=TextView(this).apply {
            text=state.marker+" DERNIER ÉTAT MÉMORISÉ : "+state.label+
                " · "+since+" (PAS EN DIRECT)"
            textSize=16f
            setTextColor(0xFF2F3038.toInt())
            setPadding(0,0,0,dp(8))
        }
        val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val refresh=Button(this).apply { text="🔄"; textSize=20f; contentDescription="Recharger seulement "+repo }
        val targetButton=Button(this).apply { text="🎯 CIBLE"; textSize=15f }
        val openButton=Button(this).apply { text="↗ OUVRIR"; textSize=15f }
        buttons.addView(refresh,LinearLayout.LayoutParams(0,dp(52),1f))
        buttons.addView(targetButton,LinearLayout.LayoutParams(0,dp(52),2f))
        buttons.addView(openButton,LinearLayout.LayoutParams(0,dp(52),2f))
        val detail=TextView(this).apply {
            text="Page : "+when(kind) {
                GithubMosaicTargets.Kind.RUN -> "dernier build connu"
                GithubMosaicTargets.Kind.ACTIONS -> "GitHub Actions"
                GithubMosaicTargets.Kind.COMMITS -> "dernier commit connu"
            }+" · non chargée"
            textSize=16f
            setTextColor(0xFF303040.toInt())
            setPadding(dp(2),dp(6),dp(2),dp(6))
        }
        val preview=FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            layoutParams=LinearLayout.LayoutParams(-1,dp(310))
        }
        val counter=TextView(this).apply {
            text="WebView inactif · aucune requête Chromium déclenchée"
            textSize=13f
            setTextColor(0xFF444444.toInt())
            setPadding(dp(2),dp(8),dp(2),dp(6))
        }
        frame.addView(header)
        frame.addView(label)
        frame.addView(buttons)
        frame.addView(detail)
        frame.addView(preview)
        frame.addView(counter)

        val tile=Tile(repo,kind,url,frame,preview,label,detail,counter)
        showPlaceholder(tile,"▶ Appuyer sur 🔄 pour charger la vraie page GitHub.")
        refresh.setOnClickListener { refreshOne(tile) }
        targetButton.setOnClickListener { chooseTarget(tile) }
        openButton.setOnClickListener { openExternal(tile.url) }
        preview.setOnClickListener { if(tile.web==null) refreshOne(tile) }

        // Header and preview gestures are separate: single header tap reads,
        // double tap opens chooser, long press refreshes only this tile.
        val headerDetector=gesture(tile,clickToSpeak=true)
        header.setOnTouchListener { _,event ->
            headerDetector.onTouchEvent(event)
            true
        }
        header.contentDescription="Projet "+repo+". Appui simple : lire. "+
            "Double appui : ouvrir. Appui long : recharger."
        preview.setOnLongClickListener {
            refreshOne(tile)
            true
        }
        return tile
    }

    private fun gesture(tile:Tile,clickToSpeak:Boolean):GestureDetector {
        return GestureDetector(this,object:GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event:MotionEvent):Boolean=true
            override fun onSingleTapConfirmed(event:MotionEvent):Boolean {
                if(clickToSpeak) {
                    val text=tile.repo+". "+tile.label.text.toString()
                    speakHeader(text)
                }
                return true
            }
            override fun onDoubleTap(event:MotionEvent):Boolean {
                openExternal(tile.url)
                return true
            }
            override fun onLongPress(event:MotionEvent) {
                refreshOne(tile)
            }
        })
    }

    private fun speakHeader(text:String) {
        // The main Dashboard still uses local Supertonic 3.
        // This optional mosaic header uses system TTS for a lightweight
        // accessibility cue while multiple Chromium views are active.
        val speech=systemSpeech
        if(speech==null) {
            pendingSpeech=text
            systemSpeech=android.speech.tts.TextToSpeech(this) { result ->
                if(!alive)return@TextToSpeech
                if(result==android.speech.tts.TextToSpeech.SUCCESS) {
                    voiceInitialized=true
                    systemSpeech?.language=Locale.FRANCE
                    pendingSpeech?.let { pending ->
                        systemSpeech?.speak(pending,
                            android.speech.tts.TextToSpeech.QUEUE_FLUSH,null,"mosaic-header")
                    }
                    pendingSpeech=null
                } else {
                    log("TTS système mosaïque non disponible")
                }
            }
        } else if(voiceInitialized) {
            speech.speak(text,android.speech.tts.TextToSpeech.QUEUE_FLUSH,null,"mosaic-header")
        } else {
            pendingSpeech=text
        }
    }

    private fun showPlaceholder(tile:Tile,message:String) {
        tile.preview.removeAllViews()
        val bitmap=tile.frozen
        if(bitmap!=null && !bitmap.isRecycled) {
            tile.preview.addView(ImageView(this).apply {
                setImageBitmap(bitmap)
                scaleType=ImageView.ScaleType.FIT_XY
                contentDescription="Capture figée, non actualisée, de "+tile.repo
            },FrameLayout.LayoutParams(-1,-1))
            val overlay=TextView(this).apply {
                text="⏸ CAPTURE FIGÉE — NON EN DIRECT"
                textSize=15f
                setTextColor(Color.WHITE)
                setBackgroundColor(0xCC333333.toInt())
                setPadding(dp(8),dp(8),dp(8),dp(8))
            }
            tile.preview.addView(overlay,FrameLayout.LayoutParams(-1,-2,
                android.view.Gravity.TOP))
        } else {
            val title=TextView(this).apply {
                text=message
                textSize=19f
                setTextColor(Color.DKGRAY)
                gravity=android.view.Gravity.CENTER
                setPadding(dp(12),dp(12),dp(12),dp(12))
            }
            tile.preview.addView(title,FrameLayout.LayoutParams(-1,-1))
        }
    }

    private fun chooseTarget(tile:Tile) {
        val values=GithubMosaicTargets.Kind.entries
        val labels=arrayOf(
            "Dernier build connu (URL exacte)",
            "Liste des workflows GitHub Actions",
            "Dernier commit connu"
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("Cible à surveiller : "+tile.repo)
            .setSingleChoiceItems(labels,values.indexOf(tile.kind)) { dialog,index ->
                tile.kind=values[index]
                tile.url=targetFor(tile.repo,tile.kind)
                prefs.edit().putString("mosaic_target_"+tile.repo,tile.kind.name).apply()
                tile.detail.text="Cible enregistrée : "+tile.url.substringAfter(".com/")
                dialog.dismiss()
                // Do not trigger any network by changing a preference.
                park(tile)
                tile.frozen?.recycle()
                tile.frozen=null
                showPlaceholder(tile,"Nouvelle cible prête. Appuyer sur 🔄 pour charger.")
            }
            .setNegativeButton("ANNULER",null)
            .show()
    }

    private fun chooseLimit() {
        val limits=intArrayOf(2,3,4,6)
        val labels=arrayOf(
            "2 fenêtres actives — économie maximale",
            "3 fenêtres actives — recommandé",
            "4 fenêtres actives — intermédiaire",
            "6 fenêtres actives — mémoire plus élevée"
        )
        val selected=limits.indexOf(liveLimit).coerceAtLeast(1)
        android.app.AlertDialog.Builder(this)
            .setTitle("Budget Chromium mutualisé")
            .setSingleChoiceItems(labels,selected) { dialog,index ->
                liveLimit=limits[index]
                prefs.edit().putInt("mosaic_live_limit",liveLimit).apply()
                while(active.size>liveLimit) park(active.entries.first().value)
                status.text="Maximum "+liveLimit+" WebViews actifs. "+
                    "Les autres cartes conservent une image FIGÉE."
                log("Limite WebView ajustée : "+liveLimit)
                dialog.dismiss()
            }
            .setNegativeButton("ANNULER",null)
            .show()
    }

    private fun openExternal(url:String) {
        if(!GithubMosaicTargets.isGithubPage(url)) {
            status.text="Navigation externe refusée : URL non GitHub."
            return
        }
        try {
            val intent=Intent(Intent.ACTION_VIEW,Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
            startActivity(Intent.createChooser(intent,"Ouvrir avec le navigateur"))
            log("Ouverture navigateur externe : github.com (cible conservée)")
        } catch(e:Exception) {
            status.text="Aucun navigateur disponible pour cette page."
            log("Navigateur externe indisponible : "+e.javaClass.simpleName)
        }
    }

    private fun refreshOne(tile:Tile) {
        if(batchActive) {
            // Individual command supersedes a running global queue.
            ++batchGeneration
            batchActive=false
            allButton.isEnabled=true
        }
        tile.afterLoad=null
        load(tile)
    }

    private fun refreshAll() {
        if(tiles.isEmpty()) return
        ++batchGeneration
        val generation=batchGeneration
        batchActive=true
        allButton.isEnabled=false
        val queue=ArrayDeque(tiles.values)
        status.text="Rechargement manuel : 0 / "+queue.size+
            " cartes · les chargements sont séquencés."
        var completed=0
        fun next() {
            if(!alive || generation!=batchGeneration) return
            val tile=queue.removeFirstOrNull()
            if(tile==null) {
                batchActive=false
                allButton.isEnabled=true
                status.text="Rechargement manuel terminé : "+completed+" cartes. "+
                    "Actives : "+active.size+" / "+liveLimit+
                    ". Les anciennes captures sont figées."
                log("Lot manuel terminé : "+completed+
                    " pages, requêtes ressources observées="+statsResources+
                    ", hôtes API internes observés="+statsBrowserApi)
                return
            }
            tile.afterLoad={
                completed++
                status.text="Rechargement manuel : "+completed+" / "+tiles.size+
                    " cartes · "+tile.repo
                mainHandler.postDelayed({ next() },700L)
            }
            load(tile)
            // A hung page must not freeze the whole user-requested batch.
            val callback=tile.afterLoad
            mainHandler.postDelayed({
                if(!alive || generation!=batchGeneration) return@postDelayed
                if(tile.afterLoad===callback && callback!=null) {
                    tile.detail.text="⚪ Délai de chargement dépassé. "+
                        "Page non vérifiée ; lien externe toujours disponible."
                    tile.afterLoad=null
                    callback.invoke()
                }
            },18000L)
        }
        next()
    }

    private fun loaded(tile:Tile) {
        if(!alive)return
        val callback=tile.afterLoad
        tile.afterLoad=null
        callback?.invoke()
    }

    private fun park(tile:Tile) {
        val web=tile.web ?: return
        // A bitmap is a real rendered preview, but is ALWAYS marked frozen.
        runCatching {
            if(web.width>0 && web.height>0 && tile.wasLoaded) {
                val width=minOf(web.width,420)
                val height=minOf(web.height,340)
                val screenshot=Bitmap.createBitmap(width,height,Bitmap.Config.RGB_565)
                val canvas=Canvas(screenshot)
                canvas.scale(width.toFloat()/web.width,height.toFloat()/web.height)
                web.draw(canvas)
                tile.frozen?.recycle()
                tile.frozen=screenshot
            }
        }.onFailure {
            log("Snapshot indisponible "+tile.repo+" : "+it.javaClass.simpleName)
        }
        tile.afterLoad=null
        tile.web=null
        active.remove(tile.repo)
        tile.preview.removeView(web)
        runCatching {
            web.stopLoading()
            web.loadUrl("about:blank")
            web.destroy()
        }
        showPlaceholder(tile,"⏸ Fenêtre Chromium libérée. Appuyer sur 🔄 pour réactiver.")
        tile.counter.text="⏸ Capture figée, fenêtre Chromium libérée. "+
            "Recharger pour revenir en direct."
    }

    private fun load(tile:Tile) {
        if(!alive)return
        val existing=tile.web
        if(existing!=null) {
            active[tile.repo]=tile
            tile.wasLoaded=false
            tile.lastLoadStart=SystemClock.elapsedRealtime()
            tile.resourceCount.set(0)
            tile.browserApiCount.set(0)
            tile.domChanges=0
            tile.detail.text="⏳ Rechargement individuel en cours : "+tile.repo
            log("ACTUALISER individuel : "+tile.repo)
            existing.reload()
            return
        }
        while(active.size>=liveLimit) {
            park(active.entries.first().value)
        }
        buildWebView(tile)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(tile:Tile) {
        val web=WebView(this)
        tile.web=web
        active[tile.repo]=tile
        tile.wasLoaded=false
        tile.lastLoadStart=SystemClock.elapsedRealtime()
        tile.resourceCount.set(0)
        tile.browserApiCount.set(0)
        tile.domChanges=0
        with(web.settings) {
            javaScriptEnabled=true
            domStorageEnabled=true
            useWideViewPort=true
            loadWithOverviewMode=false
            textZoom=155
            setSupportZoom(false)
            builtInZoomControls=false
            displayZoomControls=false
            allowFileAccess=false
            allowContentAccess=false
            javaScriptCanOpenWindowsAutomatically=false
            setSupportMultipleWindows(false)
            mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture=true
        }
        web.setInitialScale(135)
        web.setBackgroundColor(Color.WHITE)
        web.isVerticalScrollBarEnabled=true
        web.isHorizontalScrollBarEnabled=true
        web.webChromeClient=object:WebChromeClient() {
            override fun onConsoleMessage(message:ConsoleMessage):Boolean {
                if(message.message().startsWith("DASHGIT_DOM_SIGNAL") &&
                    tile.web===web) {
                    tile.domChanges++
                    tile.detail.text="🔵 Changement de page observé (DOM). "+
                        "Le statut exact reste celui affiché par GitHub."
                    log("DOM spontané "+tile.repo+" (signal "+tile.domChanges+
                        ", ne prouve pas un changement de build)")
                    return true
                }
                return false
            }
        }
        web.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view:WebView,request:WebResourceRequest
            ):Boolean {
                if(!request.isForMainFrame) return false
                val next=request.url.toString()
                if(!GithubMosaicTargets.isGithubPage(next)) {
                    // Navigation out of GitHub is user-directed; never embed it.
                    tile.detail.text="Navigation hors GitHub bloquée dans la mosaïque."
                    return true
                }
                // Clicking another GitHub page should not silently change
                // the exact tracked workflow/commit.
                if(next.substringBefore("#")!=tile.url.substringBefore("#")) {
                    openExternal(next)
                    return true
                }
                return false
            }
            override fun shouldInterceptRequest(
                view:WebView,request:WebResourceRequest
            ):WebResourceResponse? {
                // Observe, do not block, rewrite or scrape requests.
                tile.resourceCount.incrementAndGet()
                if(request.url.host=="api.github.com") {
                    tile.browserApiCount.incrementAndGet()
                }
                return null
            }
            override fun onPageStarted(view:WebView,url:String?,favicon:Bitmap?) {
                tile.wasLoaded=false
                tile.detail.text="⏳ GitHub charge "+tile.repo+"…"
                tile.counter.text="Chargement Chromium. Réseau interne à GitHub possible."
            }
            override fun onPageFinished(view:WebView,url:String?) {
                if(tile.web!==web || !alive)return
                tile.wasLoaded=true
                statsPageLoads++
                val resources=tile.resourceCount.get()
                val api=tile.browserApiCount.get()
                statsResources+=resources
                statsBrowserApi+=api
                val duration=SystemClock.elapsedRealtime()-tile.lastLoadStart
                tile.detail.text=if(url?.contains("/login")==true)
                    "🔐 GitHub demande une authentification. Ouvrir avec Chrome."
                    else "✅ Page GitHub chargée · contenu visuel directement affiché."
                tile.counter.text="Chromium : "+resources+" requêtes ressources observées "+
                    "(dont "+api+" vers api.github.com), "+duration+" ms. "+
                    "Ce compteur n'est PAS un quota API REST Dashboard."
                log("Chargement "+tile.repo+" : "+duration+" ms, "+
                    resources+" ressources Chromium, api.github.com="+api)
                if(GithubMosaicTargets.isGithubPage(url ?: "")) {
                    attachDomObservation(web)
                }
                loaded(tile)
            }
            override fun onReceivedError(
                view:WebView,request:WebResourceRequest,error:WebResourceError
            ) {
                if(!request.isForMainFrame || tile.web!==web)return
                tile.detail.text="⚪ GitHub inaccessible : "+error.errorCode+
                    ". Ouvrir dans Chrome pour vérifier."
                log("WebView erreur "+tile.repo+" : code "+error.errorCode)
                loaded(tile)
            }
            override fun onReceivedHttpError(
                view:WebView,request:WebResourceRequest,response:WebResourceResponse
            ) {
                if(!request.isForMainFrame || tile.web!==web)return
                if(response.statusCode>=400) {
                    tile.detail.text="⚪ GitHub HTTP "+response.statusCode+
                        ". Page non vérifiée ; ouvrir avec Chrome."
                    log("WebView HTTP "+response.statusCode+" : "+tile.repo)
                    loaded(tile)
                }
            }
            override fun onRenderProcessGone(
                view:WebView,detail:RenderProcessGoneDetail
            ):Boolean {
                // A heavy Chromium renderer can die; fail safely, never
                // terminate the whole Dashboard app.
                if(tile.web===web) {
                    tile.web=null
                    active.remove(tile.repo)
                    tile.preview.removeView(web)
                    web.destroy()
                    showPlaceholder(tile,
                        "⚪ Le moteur Chromium a été interrompu. "+
                        "Appuyer sur 🔄 pour rouvrir.")
                    tile.detail.text="Moteur WebView indisponible, état non confirmé."
                    tile.afterLoad?.let { completed ->
                        tile.afterLoad=null
                        completed()
                    }
                }
                log("Processus WebView disparu pour "+tile.repo+
                    ", crash="+detail.didCrash())
                return true
            }
        }

        // WebView gets its regular taps/scrolls; gesture detection observes
        // double-tap and long-press without hijacking normal navigation.
        val detector=gesture(tile,clickToSpeak=false)
        web.setOnTouchListener { _,event ->
            detector.onTouchEvent(event)
            false
        }
        web.setOnLongClickListener {
            refreshOne(tile)
            true
        }
        tile.preview.removeAllViews()
        tile.preview.addView(web,FrameLayout.LayoutParams(-1,-1))
        tile.detail.text="⏳ Chargement de la page GitHub exacte…"
        log("ACTUALISER "+tile.repo+" : "+tile.url)
        web.loadUrl(tile.url)
    }

    private fun attachDomObservation(view:WebView) {
        // MutationObserver observes the existing GitHub DOM only.
        // It does not call fetch, reload, poll or request an API.
        // A DOM signal is NOT interpreted as an authenticated build status.
        val code="""
            (function(){
              if(window.__dashgitDomObserver)return 'active';
              var root=document.querySelector('main')||document.body;
              if(!root)return 'no-root';
              var last=Date.now();
              var observer=new MutationObserver(function(list){
                var relevant=list.some(function(x){
                  return x.type==='characterData'||x.addedNodes.length>0||x.removedNodes.length>0;
                });
                if(relevant && Date.now()-last>2500){
                  last=Date.now();
                  console.log('DASHGIT_DOM_SIGNAL');
                }
              });
              observer.observe(root,{subtree:true,childList:true,characterData:true});
              window.__dashgitDomObserver=observer;
              return 'observing';
            })();
        """.trimIndent()
        runCatching { view.evaluateJavascript(code,null) }
    }

    override fun onPause() {
        super.onPause()
        active.values.forEach { it.web?.onPause() }
    }
    override fun onResume() {
        super.onResume()
        active.values.forEach { it.web?.onResume() }
    }
    override fun onDestroy() {
        alive=false
        batchGeneration++
        batchActive=false
        mainHandler.removeCallbacksAndMessages(null)
        for(tile in active.values.toList()) {
            val web=tile.web ?: continue
            tile.web=null
            tile.preview.removeView(web)
            runCatching {
                web.stopLoading()
                web.loadUrl("about:blank")
                web.destroy()
            }
        }
        active.clear()
        systemSpeech?.shutdown()
        systemSpeech=null
        tiles.values.forEach {
            it.frozen?.recycle()
            it.frozen=null
        }
        log("Fin mosaïque ; WebViews fermées. Pages chargées="+statsPageLoads+
            ", ressources observées="+statsResources+
            ", échanges WebView api.github.com observés="+statsBrowserApi)
        super.onDestroy()
    }
}
