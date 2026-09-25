package com.carx.byd;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class BydCrypto {
    private static final byte[] ZERO_IV = new byte[16];
    private BydCrypto() {}

    public static String md5Hex(String value) {
        return digestHex("MD5", value == null ? "" : value).toUpperCase(Locale.ROOT);
    }

    public static String pwdLoginKey(String password) { return md5Hex(md5Hex(password)); }

    private static String digestHex(String alg, String value) {
        try {
            MessageDigest md = MessageDigest.getInstance(alg);
            byte[] d = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(d, false);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static String sha1Mixed(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder mixed = new StringBuilder(40);
            for (int i = 0; i < digest.length; i++) {
                String h = String.format(Locale.ROOT, "%02x", digest[i] & 0xff);
                mixed.append((i % 2 == 0) ? h.toUpperCase(Locale.ROOT) : h.toLowerCase(Locale.ROOT));
            }
            StringBuilder filtered = new StringBuilder(40);
            for (int j = 0; j < mixed.length(); j++) {
                char c = mixed.charAt(j);
                if (c == '0' && j % 2 == 0) continue;
                filtered.append(c);
            }
            return filtered.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static String buildSignString(Map<String, String> fields, String passwordHash) {
        List<String> keys = new ArrayList<>(fields.keySet());
        Collections.sort(keys);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) b.append('&');
            String k = keys.get(i);
            String v = fields.get(k);
            b.append(k).append('=').append(v == null ? "null" : v);
        }
        return b.append("&password=").append(passwordHash).toString();
    }

    public static String computeCheckcode(Map<String, Object> payload) {
        String md5 = digestHex("MD5", JsonUtil.stringify(payload));
        return md5.substring(24, 32) + md5.substring(8, 16) + md5.substring(16, 24) + md5.substring(0, 8);
    }

    public static String sha256Hex(String value) {
        return digestHex("SHA-256", value == null ? "" : value).toLowerCase(Locale.ROOT);
    }

    public static String computeCnCheckcode(Map<String, Object> payload) {
        return sha256Hex(JsonUtil.stringify(payload));
    }

    public static String aesEncryptHex(String plaintext, String keyHex) {
        try {
            byte[] key = hexToBytes(keyHex);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(ZERO_IV));
            return bytesToHex(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), true);
        } catch (Exception e) { throw new IllegalStateException("AES encryption failed", e); }
    }

    public static String aesDecryptUtf8(String cipherHex, String keyHex) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(hexToBytes(keyHex), "AES"), new IvParameterSpec(ZERO_IV));
            return new String(cipher.doFinal(hexToBytes(cipherHex)), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("AES decryption failed", e); }
    }

    public static byte[] hexToBytes(String hex) {
        String s = hex.replace("0x", "").replace("0X", "").trim();
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    public static String bytesToHex(byte[] data, boolean upper) {
        StringBuilder b = new StringBuilder(data.length * 2);
        for (byte v : data) b.append(String.format(Locale.ROOT, "%02x", v & 0xff));
        String s = b.toString();
        return upper ? s.toUpperCase(Locale.ROOT) : s;
    }
}
