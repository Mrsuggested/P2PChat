package com.example.p2pchat.crypto;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256/GCM 端到端加密工具
 * 每条消息独立密钥 + 独立IV，确保前向安全性
 */
public class CryptoUtils {
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12;   // bytes

    /**
     * 生成 256 位会话密钥
     */
    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, new SecureRandom());
        return keyGen.generateKey();
    }

    /**
     * 将密钥编码为 Base64 字符串（用于交换）
     */
    public static String keyToBase64(SecretKey key) {
        return Base64.encodeToString(key.getEncoded(), Base64.NO_WRAP);
    }

    /**
     * 从 Base64 字符串恢复密钥
     */
    public static SecretKey keyFromBase64(String base64Key) {
        byte[] decoded = Base64.decode(base64Key, Base64.NO_WRAP);
        return new SecretKeySpec(decoded, "AES");
    }

    /**
     * AES-GCM 加密
     * @param plaintext 明文
     * @param key 密钥
     * @return Base64 编码的密文（IV + 密文 + TAG）
     */
    public static String encrypt(String plaintext, SecretKey key) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_GCM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        // IV + 密文 拼接后 Base64 编码
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    /**
     * AES-GCM 解密
     * @param encryptedBase64 Base64 编码的密文
     * @param key 密钥
     * @return 明文
     */
    public static String decrypt(String encryptedBase64, SecretKey key) throws Exception {
        byte[] combined = Base64.decode(encryptedBase64, Base64.NO_WRAP);

        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(AES_GCM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
