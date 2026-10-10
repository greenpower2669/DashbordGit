package fr.fab.dashboardgit

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class Supertonic3Speech(
    private val context: Context,
    private val onDebug: (String) -> Unit
) {
    companion object {
        private const val DIR = "tts/supertonic3"
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
        onDebug("Supertonic 3 : initialisation INT8")
        val created=OfflineTts(
            assetManager=context.assets,
            config=OfflineTtsConfig(
                model=OfflineTtsModelConfig(
                    supertonic=OfflineTtsSupertonicModelConfig(
                        durationPredictor="$DIR/duration_predictor.int8.onnx",
                        textEncoder="$DIR/text_encoder.int8.onnx",
                        vectorEstimator="$DIR/vector_estimator.int8.onnx",
                        vocoder="$DIR/vocoder.int8.onnx",
                        ttsJson="$DIR/tts.json",
                        unicodeIndexer="$DIR/unicode_indexer.bin",
                        voiceStyle="$DIR/voice.bin"
                    ),
                    numThreads=2,
                    debug=false,
                    provider="cpu"
                ),
                maxNumSentences=1,
                silenceScale=0.2f
            )
        )
        require(created.numSpeakers() >= 10) {
            "Supertonic 3 : 10 voix attendues, ${created.numSpeakers()} trouvées"
        }
        tts=created
        onDebug("Supertonic 3 prêt : ${created.sampleRate()} Hz, ${created.numSpeakers()} voix")
        return created
    }

    fun speak(text:String,sid:Int,seed:Int,onFailure:(Throwable)->Unit) {
        val voice=sid.coerceIn(0,9)
        val mine=requestId.incrementAndGet()
        executor.queue.clear()
        stopTrack()
        executor.execute {
            try {
                if(mine!=requestId.get()) return@execute
                val e=engine()
                if(mine!=requestId.get()) return@execute
                onDebug("Supertonic 3 : génération FR, SID $voice, seed $seed, ${text.length} caractères")
                val audio=e.generateWithConfig(
                    text=text,
                    config=GenerationConfig(
                        sid=voice,
                        speed=1.0f,
                        numSteps=8,
                        silenceScale=0.2f,
                        extra=mapOf(
                            "lang" to "fr",
                            "seed" to seed.toString()
                        )
                    )
                )
                if(mine!=requestId.get()) {
                    onDebug("Supertonic 3 : ancienne parole annulée")
                    return@execute
                }
                require(audio.samples.isNotEmpty()) { "Supertonic 3 n'a généré aucun son" }
                onDebug("Supertonic 3 : audio généré, ${audio.samples.size} échantillons")
                play(audio.samples,audio.sampleRate,mine)
                if(mine==requestId.get()) onDebug("Supertonic 3 : parole terminée")
            } catch(t:Throwable) {
                if(mine!=requestId.get()) {
                    onDebug("Supertonic 3 : génération annulée")
                    return@execute
                }
                onDebug("Erreur Supertonic 3 : ${t.javaClass.simpleName}: ${t.message}")
                onFailure(t)
            }
        }
    }

    private fun play(samples:FloatArray,sampleRate:Int,mine:Int) {
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
        if(mine!=requestId.get()) {
            player.release()
            return
        }
        track=player
        player.play()
        val durationMs=(samples.size * 1000L / sampleRate).coerceAtLeast(100L)
        Thread.sleep(durationMs + 120L)
        if(track===player) track=null
        runCatching { player.stop() }
        player.release()
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
