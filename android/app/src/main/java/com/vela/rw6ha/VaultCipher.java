package com.vela.rw6ha;

import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class VaultCipher {
    private static final byte[] AAD = "com.vela.rw6ha/vault/v1".getBytes(StandardCharsets.UTF_8);
    static byte[] encrypt(SecretKey key,byte[] plain) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key); cipher.updateAAD(AAD);
        byte[] encrypted=cipher.doFinal(plain),iv=cipher.getIV();
        if(iv.length!=12)throw new IllegalStateException("Invalid IV");
        byte[] blob=new byte[13+encrypted.length];blob[0]=1;
        System.arraycopy(iv,0,blob,1,12);System.arraycopy(encrypted,0,blob,13,encrypted.length);return blob;
    }
    static byte[] decrypt(SecretKey key,byte[] blob) throws Exception {
        if(blob.length<29 || blob.length>128*1024 || blob[0]!=1)throw new IllegalArgumentException("Invalid vault");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,blob,1,12));cipher.updateAAD(AAD);
        return cipher.doFinal(blob,13,blob.length-13);
    }
}
