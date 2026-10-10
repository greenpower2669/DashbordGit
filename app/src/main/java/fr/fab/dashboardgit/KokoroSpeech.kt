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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import java.io.FileOutputStream

class KokoroSpeech(
    private val context: Context,
    private val onDebug: (String) -> Unit
) {
    companion object {
        private const val MODEL_DIR = "kokoro-int8-multi-lang-v1_1"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val requestId = AtomicInteger(0)
    @Volatile private var engine: OfflineTts? = null
    @Volatile private var track: AudioTrack? = null

    private fun engine(): OfflineTts {
        engine?.let { return it }
        return synchronized(this) {
            engine ?: run {
                onDebug("Kokoro : préparation des données eSpeak")
                val dataDir = ensureEspeakData()
                onDebug("Kokoro : initialisation Sherpa-ONNX")
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
                    engine = it
                    onDebug("Kokoro local prêt : ${it.sampleRate()} Hz, ${it.numSpeakers()} voix")
                }
            }
        }
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

    fun speak(text: String, sid: Int = 3, onFailure: (Throwable) -> Unit) {
        val mine = requestId.incrementAndGet()
        stopTrack()
        executor.execute {
            try {
                onDebug("Kokoro : demande de parole, ${text.length} caractères")
                val tts = engine()
                val safeSid = sid.coerceIn(0, (tts.numSpeakers() - 1).coerceAtLeast(0))
                onDebug("Kokoro : génération audio, voix $safeSid")
                val audio = tts.generateWithConfig(
                    text,
                    GenerationConfig(
                        sid = safeSid,
                        speed = 1.0f,
                        silenceScale = 0.2f
                    )
                )
                if (mine != requestId.get()) return@execute
                require(audio.samples.isNotEmpty()) { "Kokoro n'a généré aucun son" }
                onDebug("Kokoro : audio généré, ${audio.samples.size} échantillons")
                play(audio.samples, audio.sampleRate, mine)
            } catch (t: Throwable) {
                onDebug("Erreur Kokoro : ${t.javaClass.simpleName}: ${t.message}")
                onFailure(t)
            }
        }
    }

    private fun play(samples: FloatArray, sampleRate: Int, mine: Int) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(sampleRate)
        val player = AudioTrack.Builder()
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

        track = player
        player.play()
        var offset = 0
        while (offset < samples.size && mine == requestId.get()) {
            val count = minOf(4096, samples.size - offset)
            val written = player.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) error("Lecture audio Kokoro impossible ($written)")
            offset += written
        }
        while (mine == requestId.get() && player.playbackHeadPosition < offset) {
            Thread.sleep(15)
        }
        if (track === player) track = null
        runCatching { player.stop() }
        player.release()
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
        stopTrack()
        executor.shutdownNow()
        synchronized(this) {
            runCatching { engine?.release() }
            engine = null
        }
    }
}
