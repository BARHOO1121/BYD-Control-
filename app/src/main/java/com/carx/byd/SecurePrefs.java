package com.carx.byd;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecurePrefs {
    private static final String ALIAS = "carx_byd_credentials_v1";
    private final SharedPreferences prefs;

    public SecurePrefs(Context c) { prefs = c.getSharedPreferences("carx_secure", Context.MODE_PRIVATE); }

    public void put(String key, String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] ct = cipher.doFinal((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            ByteBuffer b = ByteBuffer.allocate(4 + iv.length + ct.length);
            b.putInt(iv.length).put(iv).put(ct);
            prefs.edit().putString(key, Base64.encodeToString(b.array(), Base64.NO_WRAP)).apply();
        } catch (Exception e) { throw new IllegalStateException("Secure storage failed", e); }
    }

    public String get(String key, String fallback) {
        String enc = prefs.getString(key, null);
        if (enc == null) return fallback;
        try {
            ByteBuffer b = ByteBuffer.wrap(Base64.decode(enc, Base64.NO_WRAP));
            int ivLen = b.getInt();
            if (ivLen < 12 || ivLen > 32) return fallback;
            byte[] iv = new byte[ivLen]; b.get(iv);
            byte[] ct = new byte[b.remaining()]; b.get(ct);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) { return fallback; }
    }

    public void clear() { prefs.edit().clear().apply(); }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return kg.generateKey();
    }
}
