package cn.family.learning.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionStore(context: Context): ApiSession {
    private val prefs=context.getSharedPreferences("session",Context.MODE_PRIVATE)
    private val alias="family-learning-session"
    @Volatile override var credentials: JSONObject? = load(); private set
    @Volatile override var generation: Long = 0; private set
    @Volatile override var epoch: Long = 0; private set
    override var baseUrl: String
        get()=prefs.getString("baseUrl",if(cn.family.learning.BuildConfig.DEBUG) "http://10.0.2.2:8080" else "")!!.let{raw->runCatching{ServerAddresses.normalize(raw,true)}.getOrDefault(raw)}
        @Synchronized set(value){val normalized=value.trim().trimEnd('/');if(normalized!=baseUrl){clear();prefs.edit().putString("baseUrl",normalized).commit()}}
    val accountId: String get()=credentials?.optString("accountId")?:""
    private fun key(): SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (ks.getKey(alias,null) as? SecretKey)?.let {return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun load(): JSONObject? = try {
        val raw=prefs.getString("encrypted",null)
        if(raw==null)null else {val parts=raw.split('.');val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));JSONObject(String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),Charsets.UTF_8))}
    }catch(_: Exception){prefs.edit().remove("encrypted").apply();null}
    @Synchronized fun save(value: JSONObject) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val raw=Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+"."+Base64.encodeToString(cipher.doFinal(value.toString().toByteArray()),Base64.NO_WRAP)
        prefs.edit().putString("encrypted",raw).commit();credentials=value;generation++
    }
    @Synchronized override fun saveIfCurrent(expected: Long,value: JSONObject): Boolean {if(generation!=expected)return false;save(value);return true}
    @Synchronized fun clear(){credentials=null;generation++;epoch++;prefs.edit().remove("encrypted").commit()}
    @Synchronized override fun clearIfCurrent(expected: Long){if(generation==expected)clear()}
}
