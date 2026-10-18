package fr.fab.dashboardgit

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicitly-opened browser of Djipie's PRIVATE audio catalogue. Nothing
 * loads or downloads on launch. Plays only local SHA-256 verified files.
 */
class DjipieAudioActivity:AppCompatActivity() {
    private val vault by lazy { DjipieTokenVault(this) }
    private val source by lazy { DjipiePrivateAudio(this,vault) }
    private lateinit var status:TextView
    private lateinit var search:EditText
    private lateinit var folders:Spinner
    private lateinit var results:LinearLayout
    private lateinit var countLabel:TextView
    private lateinit var more:Button
    private lateinit var stop:Button
    private var entries:List<DjipieAudioEntry> = emptyList()
    private var categoryNames=listOf("TOUS")
    private var category="TOUS"
    private var visibleLimit=20
    private var exportPending:DjipieAudioEntry?=null
    private var player:MediaPlayer?=null
    private var playingId:String?=null
    private val working=AtomicBoolean(false)
    private val cancelling=AtomicBoolean(false)
    @Volatile private var closed=false
    private val requestExport=8301

    private fun dp(value:Int)=(resources.displayMetrics.density*value+0.5f).toInt()
    private fun heading(label:String,size:Float=18f)=TextView(this).apply {
        text=label
        textSize=size
        setTypeface(null,Typeface.BOLD)
        setPadding(dp(5),dp(9),dp(5),dp(6))
    }
    private fun button(label:String,action:()->Unit)=Button(this).apply {
        text=label
        textSize=15f
        isAllCaps=false
        minHeight=dp(50)
        setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        title="🎧 Sonothèque Djipie"
        val outer=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(14),dp(12),dp(14),dp(12))
        }
        val top=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        top.addView(button("← Retour") { finish() },LinearLayout.LayoutParams(0,-2,1f))
        top.addView(button("🔐 Connexion") { promptToken() },LinearLayout.LayoutParams(0,-2,1f))
        outer.addView(heading("🎧 DJIPIE · EXPLORATEUR DE SONS",22f))
        outer.addView(top)
        outer.addView(button("🔄 Charger le catalogue") { loadCatalogue() })
        status=TextView(this).apply {
            textSize=17f
            setPadding(dp(5),dp(10),dp(5),dp(12))
            text=if(vault.read()!=null)
                "Dépôt privé connecté. Appuie sur Charger pour lire l'index."
                else "Dépôt privé. Connecte ton jeton GitHub en lecture seule."
        }
        outer.addView(status)
        val options=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        stop=button("⏹ Arrêter") { stopPlayback(); cancelling.set(true);
            status.text="Arrêt demandé (le téléchargement sera interrompu)." }
        options.addView(stop,LinearLayout.LayoutParams(0,-2,1f))
        options.addView(button("🔓 Oublier") {
            AlertDialog.Builder(this)
                .setTitle("Déconnecter Djipie ?")
                .setMessage("Le jeton chiffré sera supprimé de ce téléphone. Les sons déjà mis en cache resteront sur l'appareil.")
                .setPositiveButton("DÉCONNECTER") { _,_ ->
                    cancelling.set(true)
                    vault.clear()
                    entries=emptyList()
                    renderFolders()
                    render()
                    status.text="Déconnecté. Aucun catalogue privé disponible."
                }.setNegativeButton("ANNULER",null).show()
        },LinearLayout.LayoutParams(0,-2,1f))
        outer.addView(options)
        outer.addView(heading("📂 Dossiers et familles"))
        folders=Spinner(this).apply {
            minimumHeight=dp(54)
            contentDescription="Choisir un dossier sonore"
        }
        outer.addView(folders)
        search=EditText(this).apply {
            hint="🔎 Rechercher un son, un mot ou un projet"
            textSize=18f
            isSingleLine=true
            contentDescription="Rechercher dans la sonothèque privée"
        }
        outer.addView(search)
        countLabel=heading("Aucun catalogue chargé",17f)
        outer.addView(countLabel)
        val scroll=ScrollView(this).apply { isFillViewport=false }
        results=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(results)
        outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        more=button("Afficher 20 sons supplémentaires") {
            visibleLimit+=20
            render()
        }
        outer.addView(more)
        setContentView(outer)
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                visibleLimit=20
                render()
            }
            override fun afterTextChanged(s:Editable?) {}
        })
        folders.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,
                                        view:View?,position:Int,id:Long) {
                category=categoryNames.getOrElse(position) { "TOUS" }
                visibleLimit=20
                render()
            }
        }
        renderFolders()
        render()
    }

    private fun promptToken() {
        val input=EditText(this).apply {
            hint="github_pat_... (lecture seule)"
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine=true
            setPadding(dp(20),dp(14),dp(20),dp(14))
        }
        AlertDialog.Builder(this)
            .setTitle("🔐 Autoriser l'accès privé à Djipie")
            .setMessage("Crée un jeton GitHub fine-grained limité au dépôt Djipie : permissions Contents (lecture seule) et Metadata (lecture). Les API ne sont appelées qu'en appuyant sur Charger ou Écouter. Le jeton reste chiffré dans Android Keystore, jamais envoyé à un autre site.")
            .setView(input)
            .setPositiveButton("ENREGISTRER") { _,_ ->
                val value=input.text.toString()
                runCatching { vault.save(value) }
                    .onSuccess { status.text="Accès enregistré localement. Appuie sur Charger." }
                    .onFailure { status.text="Connexion non enregistrée : "+it.message }
                input.text?.clear()
            }
            .setNeutralButton("GÉRER SUR GITHUB") { _,_ ->
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/settings/personal-access-tokens")))
            }
            .setNegativeButton("ANNULER",null).show()
    }

    private fun loadCatalogue() {
        if(vault.read()==null) { promptToken(); return }
        if(!working.compareAndSet(false,true)) {
            status.text="Une opération audio est déjà en cours."
            return
        }
        cancelling.set(false)
        status.text="Lecture manuelle du catalogue privé Djipie..."
        Thread {
            try {
                val fetched=source.loadCatalog()
                runOnUiThread {
                    if(closed)return@runOnUiThread
                    entries=fetched
                    renderFolders()
                    render()
                    status.text="✅ Catalogue chargé : ${fetched.size} sons, sans téléchargement audio."
                }
            } catch(e:Exception) {
                runOnUiThread { if(!closed)status.text="⚠ "+e.message.orEmpty() }
            } finally { working.set(false) }
        }.start()
    }

    private fun renderFolders() {
        val counts=entries.groupingBy { it.category }.eachCount().toSortedMap()
        categoryNames=listOf("TOUS")+counts.keys
        val labels=listOf("📚 Toutes les familles (${entries.size})")+
            counts.map { (name,n) -> "📁 $name ($n)" }
        folders.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,labels)
        val index=categoryNames.indexOf(category)
        if(index<0) category="TOUS"
        folders.setSelection(index.coerceAtLeast(0))
    }

    private fun render() {
        if(!::results.isInitialized || !::countLabel.isInitialized) return
        val found=DjipieAudioCatalog.filter(entries,category,search.text?.toString().orEmpty())
        results.removeAllViews()
        countLabel.text=if(entries.isEmpty()) "Aucun son affiché."
            else "${found.size} résultat(s) · ${minOf(visibleLimit,found.size)} affiché(s)"
        found.take(visibleLimit).forEach { entry ->
            val card=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                setPadding(dp(8),dp(8),dp(8),dp(14))
                setBackgroundColor(0xFFF4F5F8.toInt())
            }
            val name=TextView(this).apply {
                text="🎵 "+entry.name
                textSize=19f
                setTypeface(null,Typeface.BOLD)
                setTextColor(0xFF20202A.toInt())
            }
            card.addView(name)
            card.addView(TextView(this).apply {
                text=buildString {
                    append(entry.category).append(" · ").append(entry.source)
                    append(" · ").append(entry.format.uppercase())
                    append(" · ").append(DjipieAudioCatalog.sizeText(entry.sizeBytes))
                    append(" · ").append(DjipieAudioCatalog.durationText(entry.seconds))
                    if(entry.originalArchive.isNotBlank() && entry.originalArchive!="null")
                        append("\nArchive : ").append(entry.originalArchive)
                    if(entry.archivePath.isNotBlank() && entry.archivePath!="null")
                        append("\nChemin : ").append(entry.archivePath)
                    if(entry.intensity.isNotBlank())
                        append("\nÉcoute descriptive : ").append(entry.intensity)
                }
                textSize=15f
                setTextColor(0xFF363647.toInt())
            })
            val controls=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            controls.addView(button("▶ Écouter") { confirmLarge(entry) { fetchAudio(entry,null) } },
                LinearLayout.LayoutParams(0,-2,1f))
            controls.addView(button("💾 Enregistrer") { requestExport(entry) },
                LinearLayout.LayoutParams(0,-2,1f))
            card.addView(controls)
            card.addView(button("🔗 Ouvrir sur GitHub") { openGithub(entry) })
            results.addView(card,LinearLayout.LayoutParams(-1,-2).apply {
                bottomMargin=dp(8)
            })
        }
        more.visibility=if(found.size>visibleLimit) View.VISIBLE else View.GONE
    }

    private fun openGithub(entry:DjipieAudioEntry) {
        // The catalogue parser strictly validated the repo and release URL.
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(entry.releaseUrl)))
        }.onFailure { status.text="Navigateur indisponible : "+it.message }
    }

    private fun confirmLarge(item:DjipieAudioEntry,action:()->Unit) {
        if(item.sizeBytes<=64L*1024*1024) { action(); return }
        AlertDialog.Builder(this)
            .setTitle("Gros fichier audio")
            .setMessage("Ce fichier fait ${DjipieAudioCatalog.sizeText(item.sizeBytes)}. Télécharger uniquement ce son, sur demande ?")
            .setPositiveButton("TÉLÉCHARGER") { _,_ -> action() }
            .setNegativeButton("ANNULER",null).show()
    }

    private fun requestExport(item:DjipieAudioEntry) {
        exportPending=item
        val mime=when(item.format) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            else -> "audio/ogg"
        }
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type=mime
            putExtra(Intent.EXTRA_TITLE,item.name)
        }
        try { startActivityForResult(intent,requestExport) }
        catch(e:Exception) {
            exportPending=null
            status.text="Impossible d'ouvrir l'enregistrement : "+e.message
        }
    }

    @Deprecated("Uses the system file-picker for a user-selected download location")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=requestExport)return
        val item=exportPending
        exportPending=null
        val destination=if(resultCode==RESULT_OK) data?.data else null
        if(item!=null && destination!=null)
            confirmLarge(item) { fetchAudio(item,destination) }
    }

    private fun fetchAudio(item:DjipieAudioEntry,destination:Uri?) {
        if(vault.read()==null) { promptToken(); return }
        if(!working.compareAndSet(false,true)) {
            status.text="Une opération est déjà en cours, réessaie après son terme."
            return
        }
        cancelling.set(false)
        status.text="⬇ ${item.name} : vérification et téléchargement à la demande..."
        Thread {
            try {
                var lastUpdate=0L
                val file=source.obtain(item,{ done,total ->
                    val now=SystemClock.uptimeMillis()
                    if(now-lastUpdate>500 || done==total) {
                        lastUpdate=now
                        runOnUiThread {
                            if(!closed)status.text="⬇ ${item.name} : "+
                                ((100*done/total).coerceIn(0,100))+" %"
                        }
                    }
                }, { cancelling.get() || closed })
                if(destination==null) {
                    runOnUiThread { if(!closed)play(file,item) }
                } else {
                    val output=contentResolver.openOutputStream(destination,"w")
                        ?: throw java.io.IOException("Destination non accessible.")
                    output.use { out -> file.inputStream().use { it.copyTo(out) } }
                    runOnUiThread {
                        if(!closed)status.text="✅ Son enregistré : ${item.name} (SHA-256 vérifié)."
                    }
                }
            } catch(e:Exception) {
                runOnUiThread { if(!closed)status.text="⚠ ${item.name} : "+e.message.orEmpty() }
            } finally { working.set(false) }
        }.start()
    }

    private fun play(file:File,item:DjipieAudioEntry) {
        stopPlayback()
        try {
            player=MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    if(closed) return@setOnPreparedListener
                    it.start()
                    status.text="▶ Lecture : ${item.name} (fichier local vérifié)."
                }
                setOnCompletionListener {
                    status.text="✅ Écoute terminée : ${item.name}"
                    stopPlayback()
                }
                setOnErrorListener { _,_,_ ->
                    status.text="⚠ Format audio non pris en charge par le téléphone."
                    stopPlayback()
                    true
                }
                prepareAsync()
            }
            playingId=item.id
        } catch(e:Exception) {
            stopPlayback()
            status.text="⚠ Lecteur audio : "+e.message
        }
    }

    private fun stopPlayback() {
        player?.let { runCatching { it.reset(); it.release() } }
        player=null
        playingId=null
    }

    override fun onDestroy() {
        closed=true
        cancelling.set(true)
        stopPlayback()
        super.onDestroy()
    }
}
