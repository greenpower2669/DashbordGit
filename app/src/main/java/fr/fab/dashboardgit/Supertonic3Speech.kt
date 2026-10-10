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
    private val onDebug:(String)->Unit
) {
    companion object { private const val DIR="tts/supertonic3" }
    private val executor=ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,LinkedBlockingQueue())
    private val requestId=AtomicInteger(0)
    @Volatile private var tts:OfflineTts?=null
    @Volatile private var track:AudioTrack?=null

    @Synchronized
    private fun engine():OfflineTts {
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
        require(created.numSpeakers()>=10) { "10 voix attendues, ${created.numSpeakers()} trouvées" }
        tts=created
        onDebug("Supertonic 3 prêt : ${created.sampleRate()} Hz, ${created.numSpeakers()} voix")
        return created
    }

    /**
     * Default: a whole sentence. Optional small word packets are deliberately
     * user-controlled so latency and prosody can be compared on the same device.
     */
    fun speak(
        text:String,
        sid:Int,
        seed:Int,
        chunked:Boolean,
        maxWords:Int,
        onFailure:(Throwable)->Unit
    ) {
        val mine=requestId.incrementAndGet()
        executor.queue.clear()
        stopTrack()
        executor.execute {
            try {
                if(mine!=requestId.get()) return@execute
                val loadStart=android.os.SystemClock.elapsedRealtime()
                val e=engine()
                val loadMs=android.os.SystemClock.elapsedRealtime()-loadStart
                if(mine!=requestId.get()) return@execute
                val chunks=if(chunked) FrenchSpeechChunks.split(text,maxWords.coerceIn(5,40))
                    else listOf(text.trim()).filter { it.isNotBlank() }
                onDebug("Supertonic 3 : chargement "+loadMs+" ms, voix "+sid+
                    ", seed "+seed+", morcellement "+chunked+", "+chunks.size+" paquet(s)")
                for((index,chunk) in chunks.withIndex()) {
                    if(mine!=requestId.get()) return@execute
                    val begun=android.os.SystemClock.elapsedRealtime()
                    val audio=e.generateWithConfig(
                        text=chunk,
                        config=GenerationConfig(
                            sid=sid.coerceIn(0,9),
                            speed=1.0f,
                            numSteps=8,
                            silenceScale=0.2f,
                            extra=mapOf("lang" to "fr","seed" to seed.toString())
                        )
                    )
                    val elapsed=android.os.SystemClock.elapsedRealtime()-begun
                    if(mine!=requestId.get()) return@execute
                    require(audio.samples.isNotEmpty()) { "Aucun son généré" }
                    val duration=audio.samples.size*1000L/audio.sampleRate
                    onDebug("Supertonic 3 paquet "+(index+1)+"/"+chunks.size+
                        " : génération "+elapsed+" ms, audio "+duration+" ms, "+audio.samples.size+" échantillons")
                    play(audio.samples,audio.sampleRate,mine)
                }
                if(mine==requestId.get()) onDebug("Supertonic 3 : lecture terminée")
            } catch(t:Throwable) {
                if(mine!=requestId.get()) {
                    onDebug("Supertonic 3 : requête annulée")
                    return@execute
                }
                onDebug("Supertonic 3 : échec "+t.javaClass.simpleName+": "+t.message)
                onFailure(t)
            }
        }
    }

    private fun play(samples:FloatArray,sampleRate:Int,mine:Int) {
        if(samples.isEmpty()) return
        val player=AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes((samples.size*Float.SIZE_BYTES).coerceAtLeast(4096))
            .build()
        val written=player.write(samples,0,samples.size,AudioTrack.WRITE_BLOCKING)
        require(written>=0) { "AudioTrack write failed: $written" }
        if(mine!=requestId.get()) { player.release(); return }
        track=player
        player.play()
        var wait=(samples.size*1000L/sampleRate)+80L
        while(wait>0 && mine==requestId.get()) {
            val slice=minOf(50L,wait)
            Thread.sleep(slice)
            wait-=slice
        }
        if(track===player) track=null
        runCatching { player.stop() }
        player.release()
    }

    @Synchronized private fun stopTrack() {
        val p=track ?: return
        track=null
        runCatching { p.pause() }; runCatching { p.flush() }; runCatching { p.stop() }; runCatching { p.release() }
    }

    fun close() {
        requestId.incrementAndGet()
        executor.queue.clear()
        stopTrack()
        executor.shutdownNow()
        synchronized(this) { runCatching { tts?.release() }; tts=null }
    }
}
