package fr.fab.dashboardgit

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PierrePiperSpeech(
    private val context: Context,
    private val onDebug: (String) -> Unit
) {
    companion object {
        private const val ASSET_DIR = "tts/piper/pierre"
        private const val MODEL = "fr_FR-upmc-medium.onnx"
        private const val TOKENS = "tokens.txt"
        private const val DATA_DIR = "espeak-ng-data"
        private const val SPEAKER_ID = 1
    }

    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue()
    )
    private val requestId = AtomicInteger(0)
    @Volatile private var tts: OfflineTts? = null
    @Volatile private var track: AudioTrack? = null

    @Synchronized
    private fun engine(): OfflineTts {
        tts?.let { return it }
        val dataDir = prepareEspeakData()
        onDebug("Pierre/Piper : initialisation Sherpa-ONNX fr_FR-upmc-medium")
        val created = OfflineTts(
            assetManager = context.assets,
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = "$ASSET_DIR/$MODEL",
                        tokens = "$ASSET_DIR/$TOKENS",
                        dataDir = dataDir.absolutePath
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                ),
                maxNumSentences = 1,
                silenceScale = 0.2f
            )
        )
        tts = created
        onDebug("Pierre/Piper prêt : ${created.sampleRate()} Hz, ${created.numSpeakers()} voix, Pierre SID 1")
        return created
    }

    fun speak(text: String, onFailure: (Throwable) -> Unit) {
        val mine = requestId.incrementAndGet()
        executor.queue.clear()
        stopTrack()
        executor.execute {
            try {
                if (mine != requestId.get()) return@execute
                onDebug("Pierre/Piper : demande de parole, ${text.length} caractères")
                val engine = engine()
                if (mine != requestId.get()) return@execute

                val audio = engine.generate(
                    text = text,
                    sid = SPEAKER_ID,
                    speed = 1.0f
                )
                if (mine != requestId.get()) {
                    onDebug("Pierre/Piper : ancienne parole annulée")
                    return@execute
                }
                require(audio.samples.isNotEmpty()) { "Pierre/Piper n'a généré aucun son" }
                onDebug("Pierre/Piper : audio généré, ${audio.samples.size} échantillons")
                play(audio.samples, audio.sampleRate, mine)
                if (mine == requestId.get()) onDebug("Pierre/Piper : parole terminée")
            } catch (t: Throwable) {
                if (mine != requestId.get()) {
                    onDebug("Pierre/Piper : génération annulée")
                    return@execute
                }
                onDebug("Erreur Pierre/Piper : ${t.javaClass.simpleName}: ${t.message}")
                onFailure(t)
            }
        }
    }

    private fun play(samples: FloatArray, sampleRate: Int, mine: Int) {
        val bytes=(samples.size * Float.SIZE_BYTES).coerceAtLeast(4096)
        val player=AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bytes)
            .build()

        val written=player.write(samples,0,samples.size,AudioTrack.WRITE_BLOCKING)
        require(written >= 0) { "AudioTrack write failed: $written" }
        if (mine != requestId.get()) {
            player.release()
            return
        }
        track=player
        player.play()
        val durationMs=(samples.size * 1000L / sampleRate).coerceAtLeast(100L)
        Thread.sleep(durationMs + 120L)
        if (track === player) track=null
        runCatching { player.stop() }
        player.release()
    }

    private fun prepareEspeakData(): File {
        val root=File(context.filesDir,"tts_pierre")
        val destination=File(root,DATA_DIR)
        val marker=File(destination,".dashboardgit-ready-v1")
        if (!marker.exists()) {
            if (destination.exists()) destination.deleteRecursively()
            destination.mkdirs()
            copyAssetTree("$ASSET_DIR/$DATA_DIR",destination)
            marker.writeText("sherpa-onnx-v1.13.8-upmc-medium-pierre")
        }
        return destination
    }

    private fun copyAssetTree(assetPath:String,destination:File) {
        val children=context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
            return
        }
        destination.mkdirs()
        children.forEach { child ->
            copyAssetTree("$assetPath/$child",File(destination,child))
        }
    }

    @Synchronized
    private fun stopTrack() {
        val p=track ?: return
        track=null
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
            runCatching { tts?.release() }
            tts=null
        }
    }
}
