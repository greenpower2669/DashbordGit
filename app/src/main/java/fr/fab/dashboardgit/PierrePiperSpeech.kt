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
    private val onDebug:(String)->Unit
) {
    companion object {
        private const val ASSET_DIR="tts/piper/pierre"
        private const val SPEAKER_ID=1
    }
    private val executor=ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,LinkedBlockingQueue())
    private val requestId=AtomicInteger(0)
    @Volatile private var tts:OfflineTts?=null
    @Volatile private var track:AudioTrack?=null

    @Synchronized
    private fun engine():OfflineTts {
        tts?.let { return it }
        val dataDir=prepareEspeakData()
        onDebug("Piper/Pierre : initialisation fr_FR-upmc-medium")
        val created=OfflineTts(
            assetManager=context.assets,
            config=OfflineTtsConfig(
                model=OfflineTtsModelConfig(
                    vits=OfflineTtsVitsModelConfig(
                        model="$ASSET_DIR/fr_FR-upmc-medium.onnx",
                        tokens="$ASSET_DIR/tokens.txt",
                        dataDir=dataDir.absolutePath
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
        onDebug("Piper/Pierre prêt : ${created.sampleRate()} Hz, SID 1")
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
                onDebug("Piper/Pierre : ${chunks.size} paquet(s) de mots")
                for((index,chunk) in chunks.withIndex()) {
                    if(mine!=requestId.get()) return@execute
                    val audio=e.generate(chunk,SPEAKER_ID,1.0f)
                    if(mine!=requestId.get()) return@execute
                    onDebug("Piper/Pierre : paquet ${index+1}/${chunks.size}, ${audio.samples.size} échantillons")
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

    private fun prepareEspeakData():File {
        val destination=File(context.filesDir,"tts_pierre/espeak-ng-data")
        val marker=File(destination,".dashboardgit-ready-v2")
        if(!marker.exists()) {
            if(destination.exists()) destination.deleteRecursively()
            destination.mkdirs()
            copyAssetTree("$ASSET_DIR/espeak-ng-data",destination)
            marker.writeText("upmc-medium")
        }
        return destination
    }

    private fun copyAssetTree(assetPath:String,destination:File) {
        val children=context.assets.list(assetPath).orEmpty()
        if(children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input -> FileOutputStream(destination).use { output -> input.copyTo(output) } }
            return
        }
        destination.mkdirs()
        children.forEach { copyAssetTree("$assetPath/$it",File(destination,it)) }
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
