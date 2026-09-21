package com.babybuddypocket.app;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class ApiClientTest {
  @Test
  public void normalizesServerAndApiAddresses() {
    assertEquals(
        "https://baby.example/api/", ApiClient.normalize(" https://BABY.example/ ").toString());
    assertEquals(
        "https://baby.example/buddy/api/",
        ApiClient.normalize("https://baby.example/buddy/api/").toString());
  }

  @Test
  public void rejectsUnsafeConfiguration() {
    for (String value :
        new String[] {
          "http://baby.example",
          "https://user:secret@baby.example",
          "https://baby.example/?token=secret",
          "https://baby.example/#fragment",
          "https://baby.example/../else",
          "https://baby.example/%2e%2e/"
        }) assertThrows(value, IllegalArgumentException.class, () -> ApiClient.normalize(value));
  }

  @Test
  public void protectsTokenOnEveryLink() throws Exception {
    ApiClient api = new ApiClient("https://baby.example/buddy", "test");
    for (String value :
        new String[] {
          "http://baby.example/buddy/api/",
          "https://evil.example/buddy/api/",
          "https://baby.example:444/buddy/api/",
          "https://user@baby.example/buddy/api/",
          "../../admin/",
          "https://baby.example/buddy/api/%2e%2e/",
          "https://baby.example/buddy/api/feedings/#secret"
        }) assertThrows(value, IOException.class, () -> api.safeUri(value));
    assertEquals(
        "https://baby.example/buddy/api/feedings/?limit=20",
        api.safeUri("feedings/?limit=20").toString());
  }

  @Test
  public void followsAllPagesIncludingQueryRelativeLinks() throws Exception {
    List<String> paths = new ArrayList<>();
    ApiClient api =
        new ApiClient(
            "https://baby.example",
            "test",
            (method, uri, token, body) -> {
              paths.add(uri.toString());
              assertEquals("Token is passed to transport only", "test", token);
              if (paths.size() == 1)
                return "{\"count\":2,\"next\":\"?limit=200&offset=200\",\"results\":[{\"id\":1}]}";
              return "{\"count\":2,\"next\":null,\"results\":[{\"id\":2}]}";
            });
    assertEquals(2, api.list("feedings").length());
    assertEquals("https://baby.example/api/feedings/?limit=200&offset=200", paths.get(1));
  }

  @Test
  public void rejectsCrossCollectionPaginationBeforeRequest() {
    int[] calls = {0};
    ApiClient api =
        new ApiClient(
            "https://baby.example",
            "test",
            (m, u, t, b) -> {
              calls[0]++;
              return "{\"next\":\"https://baby.example/api/notes/\",\"results\":[]}";
            });
    assertThrows(IOException.class, () -> api.list("feedings"));
    assertEquals(1, calls[0]);
  }

  @Test
  public void rejectsExternalPaginationBeforeRequest() {
    int[] calls = {0};
    ApiClient api =
        new ApiClient(
            "https://baby.example",
            "test",
            (m, u, t, b) -> {
              calls[0]++;
              return "{\"next\":\"https://evil.example/api/feedings/\",\"results\":[]}";
            });
    assertThrows(IOException.class, () -> api.list("feedings"));
    assertEquals(1, calls[0]);
  }

  @Test
  public void detectsPaginationCycle() {
    ApiClient api =
        new ApiClient(
            "https://baby.example",
            "test",
            (m, u, t, b) -> "{\"next\":\"?limit=200\",\"results\":[]}");
    assertThrows(IOException.class, () -> api.list("feedings"));
  }

  @Test
  public void acceptsUnpaginatedServerVersions() throws Exception {
    ApiClient api =
        new ApiClient("https://baby.example", "test", (m, u, t, b) -> "[{\"name\":\"night\"}]");
    assertEquals("night", api.list("tags").getJSONObject(0).getString("name"));
  }

  @Test
  public void rejectsMalformedJson() {
    ApiClient api =
        new ApiClient("https://baby.example", "test", (m, u, t, b) -> "<html>Proxy login</html>");
    assertThrows(JSONException.class, () -> api.list("children"));
  }

  @Test
  public void rejectsHeaderInjection() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ApiClient("https://baby.example", "token\r\nInjected: true"));
  }
}
