package com.babybuddypocket.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.*;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Android Keystore protects the token; the app opts out of Android backup. */
public final class Credentials {
  private static final String ALIAS = "baby-buddy-pocket-token";
  private final SharedPreferences prefs;

  public Credentials(Context context) {
    prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE);
  }

  public String server() {
    return prefs.getString("server", "");
  }

  private SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (!store.containsAlias(ALIAS)) {
      KeyGenerator gen =
          KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      gen.init(
          new KeyGenParameterSpec.Builder(
                  ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      gen.generateKey();
    }
    return (SecretKey) store.getKey(ALIAS, null);
  }

  public void save(String server, String token) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key());
    String data =
        Base64.encodeToString(
            cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    if (!prefs
        .edit()
        .putString("server", ApiClient.normalize(server).toString())
        .putString("token", data)
        .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
        .commit()) throw new IllegalStateException("Unable to save connection");
  }

  public String token() throws Exception {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        key(),
        new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
    return new String(
        cipher.doFinal(Base64.decode(prefs.getString("token", ""), Base64.NO_WRAP)),
        StandardCharsets.UTF_8);
  }

  public void clear() {
    if (!prefs.edit().clear().commit())
      throw new IllegalStateException("Unable to clear saved credentials");
  }
}
