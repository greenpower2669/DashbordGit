package fr.fab.dashboardgit

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MmsFrenchSpeech(
    private val context:Context,
    private val onDebug:(String)->Unit
) {
    companion object { private const val DIR="tts/mms/fra" }
    private val executor=ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,LinkedBlockingQueue())
    private val requestId=AtomicInteger(0)
    @Volatile private var tts:OfflineTts?=null
    @Volatile private var track:AudioTrack?=null

    @Synchronized
    private fun engine():OfflineTts {
        tts?.let { return it }
        onDebug("MMS français : initialisation VITS")
        val created=OfflineTts(
            assetManager=context.assets,
            config=OfflineTtsConfig(
                model=OfflineTtsModelConfig(
                    vits=OfflineTtsVitsModelConfig(
                        model="$DIR/model.onnx",
                        tokens="$DIR/tokens.txt"
                    ),
                    numThreads=2,
                    debug=false,
                    provider="cpu"
                ),
                maxNumSentences=1,
                silenceScale=0.2f
            )
        )
        tts=created
        onDebug("MMS français prêt : ${created.sampleRate()} Hz, ${created.numSpeakers()} voix")
        return created
    }

    fun speak(text:String,onFailure:(Throwable)->Unit) {
        val mine=requestId.incrementAndGet()
        executor.queue.clear()
        stopTrack()
        executor.execute {
            try {
                val e=engine()
                val chunks=FrenchSpeechChunks.split(text)
                onDebug("MMS français : ${chunks.size} paquet(s) de mots")
                for((index,chunk) in chunks.withIndex()) {
                    if(mine!=requestId.get()) return@execute
                    val audio=e.generate(chunk,0,1.0f)
                    if(mine!=requestId.get()) return@execute
                    onDebug("MMS français : paquet ${index+1}/${chunks.size}, ${audio.samples.size} échantillons")
                    play(audio.samples,audio.sampleRate,mine)
                }
            } catch(t:Throwable) {
                if(mine==requestId.get()) onFailure(t)
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
