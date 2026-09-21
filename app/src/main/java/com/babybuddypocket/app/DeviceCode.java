package com.babybuddypocket.app;

import org.json.JSONObject;

/** Baby Buddy's Add device format. Browser session cookies are deliberately ignored. */
final class DeviceCode {
  final String server, token;

  private DeviceCode(String server, String token) {
    this.server = server;
    this.token = token;
  }

  static DeviceCode parse(String scanned) {
    try {
      if (scanned == null || scanned.length() > 16384) throw new IllegalArgumentException();
      String text = scanned.trim(), prefix = "BABYBUDDY-LOGIN:";
      if (!text.startsWith(prefix)) throw new IllegalArgumentException();
      JSONObject json = new JSONObject(text.substring(prefix.length()));
      if (!(json.get("url") instanceof String) || !(json.get("api_key") instanceof String))
        throw new IllegalArgumentException();
      String token = json.getString("api_key");
      if (!token.matches("[a-fA-F0-9]{40}")) throw new IllegalArgumentException();
      return new DeviceCode(ApiClient.normalize(json.getString("url")).toString(), token);
    } catch (Exception e) {
      // Never expose scanned content (including keys/cookies) in a message or log.
      throw new IllegalArgumentException(
          "Scan the login QR code from Baby Buddy's Add a device page. It must contain an HTTPS"
              + " server address and API key.");
    }
  }
}
