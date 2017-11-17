package com.lifebox.lifeboxapp;

import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

class Crypto {
    private final byte[] keyBytes;

    public Crypto(String key) {
        keyBytes = Base64ToByte(key);
    }

    public byte[] encrypt(byte[] plainText) throws Exception {
        Cipher cipher = getCipher(Cipher.ENCRYPT_MODE);
        return cipher.doFinal(plainText);
    }

//    public byte[] decrypt(byte[] encrypted) throws Exception {
//        Cipher cipher = getCipher(Cipher.DECRYPT_MODE);
//        return cipher.doFinal(encrypted);
//    }

    private Cipher getCipher(int cipherMode) throws Exception {
        String encryptionAlgorithm = "AES";
        SecretKeySpec keySpecification = new SecretKeySpec(keyBytes, encryptionAlgorithm);
        Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
        cipher.init(cipherMode, keySpecification);
        return cipher;
    }

    public static String makeKey(String password, String salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes("UTF-8"), 1000, 128);
        SecretKeyFactory skf;
        skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        byte[] key = skf.generateSecret(spec).getEncoded();
        return ByteToBase64(key);
    }

    private static String ByteToBase64(byte[] input) {
        return Base64.encodeToString(input, Base64.DEFAULT);
    }

    private static byte[] Base64ToByte(String input) {
        return Base64.decode(input, Base64.DEFAULT);
    }
}
