package fr.fab.dashboardgit

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tiny, optional and VISIBLE WebView navigator embedded on the home screen.
 *
 * The existing HTTP/JSON + consented REST dashboard scanner remains unchanged.
 * This companion WebView is a genuinely rendered browser preview, NOT the
 * network engine used by the HTTP scanner and NOT proof of a build status.
 * Chromium can make its own requests. Only the user pressing ACTUALISER
 * starts the bounded browse session; no random delays or stealth evasion.
 */
class HomeExplorerPreview(
    private val activity:AppCompatActivity,
    private val preferences:SharedPreferences,
    private val log:(String)->Unit,
    private val openMosaic:()->Unit
) {
    val view:LinearLayout
    private val handler=Handler(Looper.getMainLooper())
    private val frame:FrameLayout
    private val title:TextView
    private val details:TextView
    private val requests:TextView
    private val toggle:Button
    private var web:WebView?=null
    private var destroyed=false
    private var enabled=preferences.getBoolean("home_explorer_enabled",true)
    private var generation=0
    private var nextIndex=0
    private var visits=emptyList<GithubExplorerPlan.Visit>()
    private var current:GithubExplorerPlan.Visit?=null
    private var busy=false
    private var pageSettled=true
    private var lastStart=0L
    private var lastDomSignal=0L
    private val resources=AtomicInteger()
    private val apiResources=AtomicInteger()
    private var totalResources=0
    private var totalApiResources=0

    private fun dp(value:Int)=
        (activity.resources.displayMetrics.density*value+0.5f).toInt()

    init {
        view=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(8),dp(8),dp(8),dp(8))
            background=GradientDrawable().apply {
                setColor(0xFFF5F5F8.toInt())
                setStroke(dp(1),0xFF777785.toInt())
                cornerRadius=dp(14).toFloat()
            }
        }
        val heading=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val caption=TextView(activity).apply {
            text="🔎 MINI-EXPLORATEUR"
            textSize=18f
            setTextColor(Color.BLACK)
            setTypeface(null,Typeface.BOLD)
        }
        toggle=Button(activity).apply {
            text=if(enabled) "⏸" else "▶"
            contentDescription=if(enabled) "Désactiver l'aperçu Web" else "Activer l'aperçu Web"
            textSize=19f
        }
        val open=Button(activity).apply {
            text="⤢"
            textSize=20f
            contentDescription="Agrandir dans la mosaïque Chromium complète"
            setOnClickListener { openMosaic() }
        }
        heading.addView(caption,LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(open,LinearLayout.LayoutParams(dp(54),dp(48)))
        heading.addView(toggle,LinearLayout.LayoutParams(dp(54),dp(48)))
        title=TextView(activity).apply {
            text="En attente · aucune page chargée"
            textSize=17f
            setTextColor(0xFF222232.toInt())
            setPadding(dp(4),dp(2),dp(4),dp(5))
            contentDescription="Progression de l'exploration GitHub"
        }
        frame=FrameLayout(activity).apply {
            setBackgroundColor(Color.WHITE)
        }
        details=TextView(activity).apply {
            text="Aperçu réel d'une page GitHub, affiché uniquement après ACTUALISER."
            textSize=15f
            setTextColor(0xFF343448.toInt())
            setPadding(dp(4),dp(6),dp(4),dp(2))
        }
        requests=TextView(activity).apply {
            text="0 page · 0 ressource navigateur observée"
            textSize=13f
            setTextColor(0xFF4A4A4A.toInt())
        }
        view.addView(heading)
        view.addView(title)
        view.addView(frame,LinearLayout.LayoutParams(-1,dp(135)))
        view.addView(details)
        view.addView(requests)
        placeholder()
        toggle.setOnClickListener {
            enabled=!enabled
            preferences.edit().putBoolean("home_explorer_enabled",enabled).apply()
            toggle.text=if(enabled) "⏸" else "▶"
            toggle.contentDescription=if(enabled) "Désactiver l'aperçu Web" else "Activer l'aperçu Web"
            if(!enabled) {
                stop("Aperçu désactivé. Le Dashboard continue sans Chromium.")
                disposeWeb()
            } else {
                title.text="Aperçu activé · prêt pour ACTUALISER"
                details.text="Aucune navigation automatique. Lance l'actualisation pour explorer."
            }
            log("Aperçu Web "+if(enabled) "activé" else "désactivé")
        }
    }

    private fun matchesVisit(url:String?,visit:GithubExplorerPlan.Visit):Boolean {
        if(url.isNullOrBlank())return false
        return runCatching {
            val actual=Uri.parse(url)
            val expected=Uri.parse(visit.url)
            actual.scheme=="https" && actual.host=="github.com" &&
                actual.path?.trimEnd('/')==expected.path?.trimEnd('/')
        }.getOrDefault(false)
    }

    private fun placeholder() {
        frame.removeAllViews()
        frame.addView(TextView(activity).apply {
            gravity=Gravity.CENTER
            text="🌐\nFenêtre miniature\nAucun réseau avant ACTUALISER"
            textSize=17f
            setTextColor(0xFF55556A.toInt())
        },FrameLayout.LayoutParams(-1,-1))
    }

    /** Called on the UI thread by the existing, user-triggered ACTUALISER button. */
    fun start(repositories:Collection<String>) {
        check(Looper.myLooper()==Looper.getMainLooper()) { "WebView requires UI thread" }
        if(destroyed || !enabled)return
        stopQueueOnly()
        visits=GithubExplorerPlan.create(repositories)
        nextIndex=0
        totalResources=0
        totalApiResources=0
        if(visits.isEmpty()) {
            title.text="Aucun projet public à explorer"
            return
        }
        busy=true
        title.text="Exploration 0 / "+visits.size+" · démarrage manuel"
        val omitted=(repositories.size-visits.size).coerceAtLeast(0)
        details.text="Parcours visible : "+visits.size+" pages GitHub Actions."+
            (if(omitted>0) " "+omitted+" autre(s) non chargée(s) (limite de 12)." else "")+
            " Lecture de données Dashboard indépendante."
        requests.text="Aucun appel REST explicite. Les pages Chromium utilisent leur réseau."
        log("Démarrage manuel de la mini-exploration : "+visits.size+
            " pages (une WebView, délai fixe de courtoisie "+
            GithubExplorerPlan.MIN_GAP_MS+" ms)")
        loadNext(generation)
    }

    private fun stopQueueOnly() {
        generation++
        busy=false
        pageSettled=true
        handler.removeCallbacksAndMessages(null)
        web?.stopLoading()
    }

    fun stop(message:String="Exploration arrêtée par l'utilisateur.") {
        stopQueueOnly()
        title.text="⏹ "+message
        details.text="L'aperçu n'est plus en cours d'exploration. Aucun rechargement programmé."
        log(message)
    }

    private fun loadNext(expectedGeneration:Int) {
        if(destroyed || !busy || expectedGeneration!=generation || !enabled)return
        if(nextIndex>=visits.size) {
            busy=false
            current=null
            pageSettled=true
            title.text="✅ Exploration terminée · "+visits.size+" pages visitées"
            details.text="Dernière page visible. Les éventuelles évolutions internes "+
                "à GitHub ne constituent pas un statut vérifié."
            requests.text=totalResources.toString()+" ressources Chromium observées, dont "+
                totalApiResources+" accès à api.github.com (compteur partiel)"
            log("Mini-exploration terminée. WebView resources="+totalResources+
                ", internal api.github.com="+totalApiResources)
            return
        }
        val visit=visits[nextIndex++]
        current=visit
        pageSettled=false
        resources.set(0)
        apiResources.set(0)
        lastStart=SystemClock.elapsedRealtime()
        title.text="🔵 EXPLORATION "+nextIndex+" / "+visits.size+" — "+visit.repository
        details.text="Ouverture réelle de "+visit.url
        try {
            val browser=ensureWeb()
            browser.loadUrl(visit.url)
            log("WebView explore "+visit.repository+" : "+visit.url)
        } catch(e:Exception) {
            log("WebView "+visit.repository+" : "+e.javaClass.simpleName)
            settle(expectedGeneration,"Erreur du navigateur, projet non vérifié.",false)
            return
        }

        handler.postDelayed({
            if(destroyed || expectedGeneration!=generation || pageSettled)return@postDelayed
            web?.stopLoading()
            settle(expectedGeneration,"Délai de chargement dépassé (20 s).",false)
        },20000L)
    }

    private fun settle(expectedGeneration:Int,message:String,blocked:Boolean) {
        if(destroyed || expectedGeneration!=generation || pageSettled)return
        pageSettled=true
        val duration=SystemClock.elapsedRealtime()-lastStart
        val count=resources.get()
        val apiCount=apiResources.get()
        totalResources+=count
        totalApiResources+=apiCount
        requests.text="Page : "+count+" ressources vues, dont "+apiCount+
            " accès WebView à api.github.com · "+duration+" ms"
        details.text=message
        log("Page "+current?.repository+" : "+message+" ; "+
            count+" ressources, api.github.com="+apiCount+", "+duration+" ms")
        if(blocked) {
            busy=false
            web?.stopLoading()
            title.text="⚪ GitHub limite ou refuse l'accès — parcours arrêté"
            details.text=message+" Aucun contournement ou nouvelle tentative automatique."
            return
        }
        val next=expectedGeneration
        if(nextIndex<visits.size) {
            // Fixed courtesy interval, not an attempt to imitate a human.
            handler.postDelayed({ loadNext(next) },GithubExplorerPlan.MIN_GAP_MS)
        } else {
            handler.postDelayed({ loadNext(next) },GithubExplorerPlan.MIN_GAP_MS)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWeb():WebView {
        web?.let { return it }
        val browser=WebView(activity)
        with(browser.settings) {
            javaScriptEnabled=true
            domStorageEnabled=true
            allowFileAccess=false
            allowContentAccess=false
            javaScriptCanOpenWindowsAutomatically=false
            setSupportMultipleWindows(false)
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled=true
            loadsImagesAutomatically=true
            mediaPlaybackRequiresUserGesture=true
            useWideViewPort=true
            loadWithOverviewMode=true
            textZoom=110
            builtInZoomControls=false
            displayZoomControls=false
        }
        browser.setInitialScale(65)
        browser.isVerticalScrollBarEnabled=true
        browser.setBackgroundColor(Color.WHITE)
        browser.webChromeClient=object:WebChromeClient() {
            override fun onConsoleMessage(message:ConsoleMessage):Boolean {
                if(message.message()=="DASHGIT_MINI_DOM" &&
                    SystemClock.elapsedRealtime()-lastDomSignal>3500L) {
                    lastDomSignal=SystemClock.elapsedRealtime()
                    if(!destroyed && web===browser && busy) {
                        title.text="🔵 "+nextIndex+" / "+visits.size+
                            " — "+(current?.repository ?: "page")+" · DOM actif"
                        log("Mutation DOM observée dans l'aperçu (pas un changement de build prouvé)")
                    }
                    return true
                }
                return false
            }
        }
        browser.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view:WebView,request:WebResourceRequest
            ):Boolean {
                if(!request.isForMainFrame)return false
                val next=request.url.toString()
                if(next.startsWith("https://github.com/login")) {
                    settle(generation,
                        "🔐 Connexion GitHub nécessaire. Utilise le navigateur externe.",true)
                    return true
                }
                val visit=current
                if(visit==null || !matchesVisit(next,visit)) {
                    details.text="Navigation hors de la cible du mini-scanner : "+
                        "ouvrir la mosaïque pour naviguer librement."
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(
                view:WebView,request:WebResourceRequest
            ):WebResourceResponse? {
                resources.incrementAndGet()
                if(request.url.host=="api.github.com")apiResources.incrementAndGet()
                return null // Observation only: no requests altered, bypassed or fabricated.
            }

            override fun onPageFinished(view:WebView,url:String?) {
                val visit=current ?: return
                if(!busy || pageSettled || web!==browser || !matchesVisit(url,visit))return
                focusActualActions(browser)
                watchExistingDom(browser)
                settle(generation,
                    "Page réelle GitHub chargée. Aperçu miniature, "+
                    "sans interprétation automatique de la couleur.",false)
            }

            override fun onReceivedHttpError(
                view:WebView,request:WebResourceRequest,response:WebResourceResponse
            ) {
                if(!request.isForMainFrame || web!==browser || pageSettled)return
                val code=response.statusCode
                if(code>=400) {
                    val block=code==403 || code==429
                    settle(generation,"GitHub HTTP "+code+" pour "+(current?.repository ?: "?")+
                        (if(block) " : protection respectée." else " : état indéterminé."),block)
                }
            }

            override fun onReceivedError(
                view:WebView,request:WebResourceRequest,error:WebResourceError
            ) {
                if(!request.isForMainFrame || web!==browser || pageSettled)return
                settle(generation,"Échec réseau WebView ("+error.errorCode+
                    "), état non vérifié.",false)
            }

            override fun onRenderProcessGone(
                view:WebView,detail:RenderProcessGoneDetail
            ):Boolean {
                if(web===browser) {
                    val parent=browser.parent as? FrameLayout
                    parent?.removeView(browser)
                    web=null
                    runCatching { browser.destroy() }
                    placeholder()
                    settle(generation,"Moteur Chromium interrompu. Aperçu non vérifié.",true)
                }
                return true
            }
        }
        frame.removeAllViews()
        frame.addView(browser,FrameLayout.LayoutParams(-1,-1))
        web=browser
        return browser
    }

    private fun focusActualActions(browser:WebView) {
        val js="""
            (function(){
              var link=document.querySelector('main a[href*="/actions/runs/"]');
              var section=link && (link.closest('li,article')||link);
              if(section)section.scrollIntoView({block:'center',behavior:'instant'});
              return !!section;
            })()
        """.trimIndent()
        runCatching { browser.evaluateJavascript(js,null) }
    }

    private fun watchExistingDom(browser:WebView) {
        // Observe DOM activity generated by GitHub. No polling or extra HTTP.
        val js="""
            (function(){
              if(window.__dashgitMiniObserver)return 'already';
              var root=document.querySelector('main')||document.body;
              if(!root)return 'unavailable';
              var previous=Date.now();
              window.__dashgitMiniObserver=new MutationObserver(function(changes){
                if(changes.length && Date.now()-previous>3500){
                  previous=Date.now();
                  console.log('DASHGIT_MINI_DOM');
                }
              });
              window.__dashgitMiniObserver.observe(root,{
                childList:true,characterData:true,subtree:true
              });
              return 'observing';
            })()
        """.trimIndent()
        runCatching { browser.evaluateJavascript(js,null) }
    }

    private fun disposeWeb() {
        val browser=web ?: return
        web=null
        frame.removeView(browser)
        runCatching {
            browser.stopLoading()
            browser.loadUrl("about:blank")
            browser.destroy()
        }
        placeholder()
    }

    fun onPause() {
        if(busy)stop("Exploration suspendue : application quittée.")
        // Release the home preview when switching to the full MosaicActivity
        // or Chrome. This prevents retaining an extra off-screen renderer.
        disposeWeb()
    }
    fun onResume() {
        // Intentionally no automatic reload when returning to the Dashboard.
    }

    fun destroy() {
        if(destroyed)return
        stopQueueOnly()
        destroyed=true
        disposeWeb()
        handler.removeCallbacksAndMessages(null)
    }
}
