package com.vela.rw6ha;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.Test;

public class VaultCipherTest {
    private SecretKey key() throws Exception {KeyGenerator k=KeyGenerator.getInstance("AES");k.init(256);return k.generateKey();}
    @Test public void roundTripsUnicodeWithUniqueNonces() throws Exception {
        SecretKey key=key();byte[] plain="[{\"name\":\"네이버\",\"secret\":\"MY\"}]".getBytes(StandardCharsets.UTF_8);
        byte[] a=VaultCipher.encrypt(key,plain),b=VaultCipher.encrypt(key,plain);
        assertFalse(Arrays.equals(a,b));assertArrayEquals(plain,VaultCipher.decrypt(key,a));assertArrayEquals(plain,VaultCipher.decrypt(key,b));
        assertFalse(new String(a,StandardCharsets.ISO_8859_1).contains("secret"));
    }
    @Test public void wrongKeyTamperingAndTruncationFailClosed() throws Exception {
        SecretKey key=key();byte[] blob=VaultCipher.encrypt(key,"private".getBytes(StandardCharsets.UTF_8));
        assertThrows(Exception.class,()->VaultCipher.decrypt(key(),blob));
        for(int i:new int[]{0,1,12,13,blob.length-1}){byte[] modified=blob.clone();modified[i]^=1;assertThrows(Exception.class,()->VaultCipher.decrypt(key,modified));}
        for(int length:new int[]{0,1,12,28,blob.length-1})assertThrows(Exception.class,()->VaultCipher.decrypt(key,Arrays.copyOf(blob,length)));
    }
}
