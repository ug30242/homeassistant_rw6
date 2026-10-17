package com.vela.rw6ha;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Separate alias, authenticated data and no-backup file from the original TOTP app. */
final class ConfigVault {
    private static final String ALIAS="rw6_ha_config_v1";
    private final AtomicFile file;
    ConfigVault(Context context){file=new AtomicFile(new File(context.getNoBackupFilesDir(),"ha-config.v1"));}
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());generator.generateKey();
        }
        return (SecretKey)store.getKey(ALIAS,null);
    }
    Config read() throws Exception {
        if(!file.getBaseFile().exists()&&!new File(file.getBaseFile()+".bak").exists()){Config c=new Config();c.touch();return c;}
        if(file.getBaseFile().length()>128*1024||new File(file.getBaseFile()+".bak").length()>128*1024)throw new IOException("Invalid vault size");
        byte[] plain=VaultCipher.decrypt(key(),file.readFully());
        try{return Config.parse(new String(plain,StandardCharsets.UTF_8));}finally{Arrays.fill(plain,(byte)0);}
    }
    void write(Config config) throws Exception {
        byte[] plain=config.payload().getBytes(StandardCharsets.UTF_8);FileOutputStream out=null;
        try{byte[] blob=VaultCipher.encrypt(key(),plain);out=file.startWrite();out.write(blob);file.finishWrite(out);}
        catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
        finally{Arrays.fill(plain,(byte)0);}
    }
    void reset() throws Exception {file.delete();KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(ALIAS);}
}
