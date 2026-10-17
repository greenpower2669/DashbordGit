package fr.fab.dashboardgit

import android.content.Context
import android.os.StatFs
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Token never stored in plaintext, URL, exports, or debug logs. */
class DjipieTokenVault(context:Context) {
    private val prefs=context.getSharedPreferences("djipie_audio_auth",Context.MODE_PRIVATE)
    private val alias="dashboardgit.djipie.read.token.v1"
    private val keyStore:KeyStore get()=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun key():SecretKey {
        val store=keyStore
        val existing=store.getKey(alias,null) as? SecretKey
        if(existing!=null) return existing
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }
    fun save(value:String) {
        val token=value.trim()
        require(token.matches(Regex("[A-Za-z0-9_-]{20,255}"))) {
            "Jeton GitHub invalide : vérifie la saisie."
        }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        prefs.edit().putString("protected",
            Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+":"+
            Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP)).apply()
    }
    fun read():String? = runCatching {
        val stored=prefs.getString("protected",null) ?: return null
        val parts=stored.split(":",limit=2)
        if(parts.size!=2) return null
        val iv=Base64.decode(parts[0],Base64.DEFAULT)
        val ciphertext=Base64.decode(parts[1],Base64.DEFAULT)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv))
        String(cipher.doFinal(ciphertext),Charsets.UTF_8)
    }.getOrNull()
    fun clear() {
        prefs.edit().remove("protected").apply()
        runCatching { keyStore.deleteEntry(alias) }
    }
}

/**
 * All private API requests are user-triggered. Bearer credentials are sent ONLY to
 * api.github.com, and never forwarded to a redirect/CDN.
 */
class DjipiePrivateAudio(private val context:Context,private val vault:DjipieTokenVault) {
    companion object {
        const val CATALOGUE_PATH =
            "https://api.github.com/repos/greenpower2669/Djipie/contents/RESSOURCES_PARTAGEES/AUDIO/CATALOGUE/catalogue_direct.json?ref=main"
        private const val MAX_ASSET = 450L*1024*1024
        private val CDN_HOSTS=setOf(
            "release-assets.githubusercontent.com",
            "objects.githubusercontent.com",
            "github-releases.githubusercontent.com",
            "api.github.com"
        )
    }

    private fun connection(url:String,token:String?,accept:String):HttpURLConnection {
        val address=URL(url)
        if(address.protocol!="https" || (token!=null && address.host!="api.github.com"))
            throw IOException("Hôte non autorisé.")
        return (address.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects=false
            connectTimeout=15000
            readTimeout=30000
            setRequestProperty("User-Agent","DashbordGit-AudioExplorer")
            setRequestProperty("Accept",accept)
            if(token!=null) {
                setRequestProperty("Authorization","Bearer $token")
                setRequestProperty("X-GitHub-Api-Version","2022-11-28")
            }
        }
    }

    fun loadCatalog():List<DjipieAudioEntry> {
        val token=vault.read() ?: throw IOException("Connecte d'abord Djipie.")
        val conn=connection(CATALOGUE_PATH,token,"application/vnd.github+json")
        try {
            val status=conn.responseCode
            if(status!=200) throw apiFailure(status)
            val encoded=JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }).getString("content").replace("\\s".toRegex(),"")
            val text=String(Base64.decode(encoded,Base64.DEFAULT),Charsets.UTF_8)
            val files=DjipieAudioCatalog.parse(text)
            if(files.isEmpty()) throw IOException("Catalogue vide ou incompatible.")
            return files
        } finally { conn.disconnect() }
    }

    private fun apiFailure(code:Int):IOException = IOException(when(code) {
        401 -> "GitHub 401 : jeton invalide ou expiré."
        403 -> "GitHub 403 : accès refusé ou quota atteint."
        404 -> "GitHub 404 : vérifie que le jeton donne accès au dépôt privé Djipie."
        429 -> "GitHub 429 : limite réseau atteinte."
        else -> "GitHub HTTP $code."
    })

    /** Playback uses a verified local cache file; never a private URL or live stream. */
    fun cacheFile(item:DjipieAudioEntry):File =
        File(File(context.cacheDir,"djipie_sound_cache"),"audio_${item.assetId}.${item.format}")

    fun obtain(item:DjipieAudioEntry,onProgress:(Long,Long)->Unit,
               cancelled:()->Boolean):File {
        val token=vault.read() ?: throw IOException("Connexion Djipie nécessaire.")
        if(item.sizeBytes>MAX_ASSET)
            throw IOException("Ce son dépasse la limite locale de 450 Mio. Ouvre sa Release.")
        val folder=File(context.cacheDir,"djipie_sound_cache")
        if(!folder.exists() && !folder.mkdirs()) throw IOException("Cache inaccessible.")
        val output=cacheFile(item)
        if(output.isFile && output.length()==item.sizeBytes) {
            output.setLastModified(System.currentTimeMillis())
            return output
        }
        folder.listFiles()?.filter { it!=output }?.forEach { it.delete() }
        val free=StatFs(folder.absolutePath).availableBytes
        if(free < item.sizeBytes+16L*1024*1024)
            throw IOException("Espace insuffisant pour ce son : "+DjipieAudioCatalog.sizeText(item.sizeBytes))
        val partial=File(folder,output.name+".part")
        partial.delete()
        val endpoint="https://api.github.com/repos/greenpower2669/Djipie/releases/assets/${item.assetId}"
        var location=endpoint
        var privateRequest=true
        var redirectCount=0
        try {
            while(true) {
                if(cancelled()) throw IOException("Téléchargement interrompu.")
                val conn=connection(location,if(privateRequest)token else null,"application/octet-stream")
                try {
                    val code=conn.responseCode
                    if(code in 300..399) {
                        if(++redirectCount>4) throw IOException("Trop de redirections.")
                        val next=URL(URL(location),conn.getHeaderField("Location")
                            ?: throw IOException("Redirection GitHub sans destination."))
                        if(next.protocol!="https" || next.host !in CDN_HOSTS)
                            throw IOException("Redirection GitHub non autorisée.")
                        location=next.toString()
                        privateRequest=false
                        continue
                    }
                    if(code!=200) throw apiFailure(code)
                    val mime=conn.contentType.orEmpty().lowercase()
                    if(mime.contains("application/json") || mime.contains("text/html"))
                        throw IOException("GitHub n'a pas retourné de fichier audio.")
                    val digest=MessageDigest.getInstance("SHA-256")
                    var count=0L
                    val bytes=ByteArray(64*1024)
                    conn.inputStream.use { input ->
                        partial.outputStream().buffered().use { outputStream ->
                            while(true) {
                                if(cancelled()) throw IOException("Téléchargement interrompu.")
                                val n=input.read(bytes)
                                if(n<0) break
                                count+=n
                                if(count>item.sizeBytes || count>MAX_ASSET)
                                    throw IOException("Taille audio inattendue.")
                                outputStream.write(bytes,0,n)
                                digest.update(bytes,0,n)
                                onProgress(count,item.sizeBytes)
                            }
                        }
                    }
                    if(count!=item.sizeBytes) throw IOException("Téléchargement incomplet.")
                    val sha=digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    if(!sha.equals(item.sha256,ignoreCase=true))
                        throw IOException("Empreinte SHA-256 invalide, fichier refusé.")
                    if(!partial.renameTo(output)) throw IOException("Impossible de valider le cache.")
                    return output
                } finally { conn.disconnect() }
            }
        } finally { partial.delete() }
    }
}
