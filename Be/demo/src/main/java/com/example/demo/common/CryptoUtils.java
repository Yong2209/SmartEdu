package com.example.demo.common;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

public class CryptoUtils {

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi băm SHA-256", e);
        }
    }

    // BR-ADM-04: Mã hóa AES-256 cho API Key trước khi lưu
    public static String encryptAes256(String plainText, String secret) {
        try {
            byte[] key = Arrays.copyOf(secret.getBytes(StandardCharsets.UTF_8), 32);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Lỗi mã hóa AES-256", e);
        }
    }

    public static String decryptAes256(String cipherText, String secret) {
        try {
            byte[] key = Arrays.copyOf(secret.getBytes(StandardCharsets.UTF_8), 32);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi giải mã AES-256", e);
        }
    }
}
