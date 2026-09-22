package com.babybuddypocket.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** All authenticated requests are restricted to this server's HTTPS API path. */
public final class ApiClient {
  public interface Transport {
    String request(String method, URI uri, String token, String body) throws Exception;
  }

  public static final class HttpFailure extends IOException {
    public final int status;
    public final String fields;

    public HttpFailure(int status, String message) {
      super(message);
      this.status = status;
      java.util.List<String> names = new java.util.ArrayList<>();
      try {
        int start = message.indexOf('{');
        JSONObject errors = new JSONObject(message.substring(start));
        for (String key :
            new String[] {
              "child",
              "name",
              "start",
              "end",
              "time",
              "date",
              "user",
              "timer",
              "type",
              "method",
              "amount",
              "nap",
              "wet",
              "solid",
              "weight",
              "height",
              "head_circumference",
              "temperature",
              "bmi",
              "notes",
              "note",
              "tags",
              "non_field_errors"
            }) if (errors.has(key)) names.add(key);
      } catch (Exception ignored) {
      }
      fields = String.join(",", names);
    }
  }

  private final URI base;
  private final String token;
  private final Transport transport;
  private long serverMillis, observedNanos;

  public ApiClient(String server, String token) {
    this(server, token, null);
  }

  public ApiClient(String server, String token, Transport transport) {
    base = normalize(server);
    this.token = token.trim();
    if (this.token.isEmpty() || this.token.contains("\n") || this.token.contains("\r"))
      throw new IllegalArgumentException(
          "Enter a valid API token from Baby Buddy's user settings.");
    this.transport = transport == null ? this::http : transport;
  }

  public static URI normalize(String input) {
    try {
      URI uri = new URI(input.trim());
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null) throw new IllegalArgumentException();
      String path = uri.getPath() == null ? "" : uri.getPath();
      if (path.contains("..") || path.contains("\\") || !path.equals(uri.getRawPath()))
        throw new IllegalArgumentException();
      path = path.replaceAll("/+$", "");
      if (!path.endsWith("/api")) path += "/api";
      return new URI(
          "https",
          null,
          uri.getHost().toLowerCase(java.util.Locale.ROOT),
          uri.getPort(),
          path + "/",
          null,
          null);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Use your HTTPS server address, for example https://baby.example.com.");
    }
  }

  URI safeUri(String path) throws IOException {
    try {
      URI candidate = base.resolve(path).normalize();
      if (!base.getScheme().equals(candidate.getScheme())
          || !base.getHost().equalsIgnoreCase(candidate.getHost())
          || port(base) != port(candidate)
          || candidate.getUserInfo() != null
          || candidate.getFragment() != null
          || !candidate.getPath().startsWith(base.getPath())
          || candidate.getRawPath().contains("%")
          || candidate.getPath().contains("\\"))
        throw new IOException(
            "The server returned an unsafe API link. Check its proxy/HTTPS configuration.");
      return candidate;
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new IOException("The server returned an invalid API link.");
    }
  }

  private static int port(URI uri) {
    return uri.getPort() < 0 ? 443 : uri.getPort();
  }

  public JSONObject object(String method, String path, JSONObject data) throws Exception {
    return new JSONObject(
        transport.request(method, safeUri(path), token, data == null ? null : data.toString()));
  }

  public void deleteTimer(long id) throws Exception {
    try {
      transport.request("DELETE", safeUri("timers/" + id + "/"), token, null);
    } catch (HttpFailure e) {
      if (e.status != 404) throw e;
    }
  }

  void observeServerDate(long millis) {
    if (millis <= 0) return;
    serverMillis = millis;
    observedNanos = System.nanoTime();
  }

  JSONObject timerTimes(JSONObject payload, boolean sharedStart) throws Exception {
    JSONObject result = Records.copy(payload);
    if (serverMillis == 0 || !result.has("start")) return result;
    // HTTP Date has one-second precision. Stay conservatively behind the server clock.
    java.time.Instant latest =
        java.time.Instant.ofEpochMilli(
            serverMillis + (System.nanoTime() - observedNanos) / 1000000 - 1000);
    java.time.Instant start = java.time.Instant.parse(result.getString("start"));
    if (!result.has("end")) {
      if (start.isAfter(latest)) result.put("start", latest.toString());
    } else {
      java.time.Instant end = java.time.Instant.parse(result.getString("end"));
      if (end.isAfter(latest)) {
        if (!sharedStart) {
          // Keep the duration of an entirely offline session when the phone is ahead.
          result.put("start", start.minus(java.time.Duration.between(latest, end)).toString());
        } else if (latest.isBefore(start)) {
          throw new java.io.IOException("Server time precedes the timer start. Try syncing later.");
        }
        result.put("end", latest.toString());
      }
    }
    return result;
  }

  public JSONArray list(String endpoint) throws Exception {
    JSONArray all = new JSONArray();
    String next = endpoint + "/?limit=200";
    Set<String> visited = new HashSet<>();
    while (next != null) {
      URI uri = safeUri(next);
      if (!uri.getPath().equals(base.getPath() + endpoint + "/"))
        throw new IOException("Pagination changed the requested collection.");
      if (!visited.add(uri.toString()) || visited.size() > 1000)
        throw new IOException("Pagination did not finish. The previous local data has been kept.");
      String response = transport.request("GET", uri, token, null);
      if (response.trim().startsWith("[")) {
        JSONArray page = new JSONArray(response);
        for (int i = 0; i < page.length(); i++) all.put(page.getJSONObject(i));
        break;
      }
      JSONObject page = new JSONObject(response);
      JSONArray rows = page.getJSONArray("results");
      for (int i = 0; i < rows.length(); i++) all.put(rows.getJSONObject(i));
      next = page.isNull("next") ? null : page.optString("next", null);
      if (next != null && next.isEmpty()) next = null;
      if (next != null) next = uri.resolve(next).toString();
    }
    return all;
  }

  private String http(String method, URI uri, String token, String body) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
    try {
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(25000);
      connection.setRequestMethod(method);
      connection.setRequestProperty("Authorization", "Token " + token);
      connection.setRequestProperty("Accept", "application/json");
      if (body != null) {
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = connection.getOutputStream()) {
          out.write(bytes);
        }
      }
      int code = connection.getResponseCode();
      observeServerDate(connection.getHeaderFieldDate("Date", 0));
      if (code >= 300 && code < 400)
        throw new HttpFailure(
            code, "The server redirected the API request. Use its final HTTPS address.");
      InputStream stream = code < 400 ? connection.getInputStream() : connection.getErrorStream();
      String response = "";
      if (stream != null)
        try (InputStream in = stream;
            ByteArrayOutputStream out = new ByteArrayOutputStream()) {
          byte[] buffer = new byte[8192];
          int count;
          while ((count = in.read(buffer)) != -1) {
            if (out.size() + count > 16 * 1024 * 1024)
              throw new IOException("Server response is too large.");
            out.write(buffer, 0, count);
          }
          response = out.toString("UTF-8");
        }
      if (code == 401 || code == 403)
        throw new HttpFailure(
            code, "Access denied. Check your API token and this user's permissions.");
      if (code >= 400) {
        String detail =
            response.trim().startsWith("{")
                ? response.substring(0, Math.min(1200, response.length()))
                : "Check the server and try again.";
        throw new HttpFailure(code, "Server returned " + code + ". " + detail);
      }
      return response;
    } finally {
      connection.disconnect();
    }
  }
}
