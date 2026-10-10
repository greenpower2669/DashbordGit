package fr.fab.dashboardgit

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class KokoroSpeech(
    private val context: Context,
    private val onDebug: (String) -> Unit
) {
    companion object {
        private const val MODEL_DIR = "kokoro-int8-multi-lang-v1_0"
        private const val FRENCH_SIWIS_SID = 30
    }

    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue()
    )
    private val requestId = AtomicInteger(0)
    @Volatile private var engine: OfflineTts? = null
    @Volatile private var track: AudioTrack? = null

    private fun engine(): OfflineTts {
        engine?.let { return it }
        return synchronized(this) {
            engine ?: run {
                onDebug("Kokoro v1.0 : préparation des données eSpeak françaises")
                val dataDir = ensureEspeakData()
                onDebug("Kokoro v1.0 : initialisation Sherpa-ONNX, langue fr")
                OfflineTts(
                    assetManager = context.assets,
                    config = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            kokoro = OfflineTtsKokoroModelConfig(
                                model = "$MODEL_DIR/model.int8.onnx",
                                voices = "$MODEL_DIR/voices.bin",
                                tokens = "$MODEL_DIR/tokens.txt",
                                dataDir = dataDir.absolutePath,
                                lang = "fr",
                                lengthScale = 1.0f
                            ),
                            numThreads = 2,
                            debug = false,
                            provider = "cpu"
                        ),
                        maxNumSentences = 1,
                        silenceScale = 0.2f
                    )
                ).also {
                    require(it.numSpeakers() > FRENCH_SIWIS_SID) {
                        "La voix française ff_siwis (SID 30) est absente du modèle"
                    }
                    engine = it
                    onDebug("Kokoro v1.0 prêt : ${it.sampleRate()} Hz, ${it.numSpeakers()} voix, ff_siwis=30")
                }
            }
        }
    }

    fun speak(text: String, sid: Int = FRENCH_SIWIS_SID, onFailure: (Throwable) -> Unit) {
        val mine = requestId.incrementAndGet()
        executor.queue.clear()
        stopTrack()
        executor.execute {
            try {
                if (mine != requestId.get()) return@execute
                onDebug("Kokoro : demande de parole, ${text.length} caractères")
                val tts = engine()
                if (mine != requestId.get()) return@execute

                val voice = FRENCH_SIWIS_SID
                if (sid != voice) onDebug("Kokoro : SID demandé $sid ignoré, ff_siwis=30 imposée")
                val player = createTrack(tts.sampleRate())
                track = player
                player.play()
                onDebug("Kokoro : génération streaming française ff_siwis (SID 30)")

                val generated = tts.generateWithConfigAndCallback(
                    text = text,
                    config = GenerationConfig(
                        sid = voice,
                        speed = 1.0f,
                        silenceScale = 0.2f
                    )
                ) { samples ->
                    if (mine != requestId.get()) {
                        0
                    } else {
                        val written = player.write(
                            samples, 0, samples.size, AudioTrack.WRITE_BLOCKING
                        )
                        if (written > 0) 1 else 0
                    }
                }

                if (mine == requestId.get()) {
                    onDebug("Kokoro : parole terminée, ${generated.samples.size} échantillons finaux")
                } else {
                    onDebug("Kokoro : parole précédente annulée")
                }
                if (track === player) track = null
                runCatching { player.stop() }
                player.release()
            } catch (t: Throwable) {
                if (mine != requestId.get()) {
                    onDebug("Kokoro : génération annulée")
                    return@execute
                }
                onDebug("Erreur Kokoro : ${t.javaClass.simpleName}: ${t.message}")
                onFailure(t)
            }
        }
    }

    private fun createTrack(sampleRate: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(sampleRate)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun ensureEspeakData(): File {
        val assetRoot = "$MODEL_DIR/espeak-ng-data"
        val targetRoot = File(context.filesDir, "$MODEL_DIR/espeak-ng-data")
        val marker = File(targetRoot, "phontab")
        if (marker.exists() && marker.length() > 0) return targetRoot

        onDebug("Kokoro : copie locale de espeak-ng-data")
        copyAssetTree(assetRoot, targetRoot)
        require(marker.exists() && marker.length() > 0) {
            "espeak-ng-data incomplet : phontab absent"
        }
        return targetRoot
    }

    private fun copyAssetTree(assetPath: String, target: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            return
        }
        target.mkdirs()
        children.forEach { child ->
            copyAssetTree("$assetPath/$child", File(target, child))
        }
    }

    @Synchronized
    private fun stopTrack() {
        val p = track ?: return
        track = null
        runCatching { p.pause() }
        runCatching { p.flush() }
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    fun close() {
        requestId.incrementAndGet()
        executor.queue.clear()
        stopTrack()
        executor.shutdownNow()
        synchronized(this) {
            runCatching { engine?.release() }
            engine = null
        }
    }
}
