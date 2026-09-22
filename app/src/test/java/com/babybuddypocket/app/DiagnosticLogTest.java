package com.babybuddypocket.app;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class DiagnosticLogTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void excludesMessagesAndServerDataButKeepsActionableFields() throws Exception {
    File file = new File(folder.getRoot(), "log.json");
    DiagnosticLog log = new DiagnosticLog(file);
    Exception error =
        new ApiClient.HttpFailure(
            400,
            "Server returned 400. {\"start\":[\"private-name https://private.invalid"
                + " secret-token\"],\"private-child\":[\"private-value\"]}");
    error.initCause(new IOException("private-name secret-token"));
    log.record(DiagnosticLog.Event.TIMER_START_REJECTED, error);
    String stored =
        new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
    String report = new DiagnosticLog(file).report();
    for (String secret :
        new String[] {
          "private-name", "private.invalid", "secret-token", "private-child", "private-value"
        }) {
      assertFalse(stored.contains(secret));
      assertFalse(report.contains(secret));
    }
    assertTrue(report.contains("TIMER_START_REJECTED"));
    assertTrue(report.contains("HTTP 400 fields=start"));
    assertTrue(report.contains("DiagnosticLogTest.excludesMessages"));
  }

  @Test
  public void limitsEventsExpiresAndClearsAcrossRestart() throws Exception {
    File file = new File(folder.getRoot(), "log.json");
    AtomicLong now = new AtomicLong(1000000);
    DiagnosticLog log = new DiagnosticLog(file, now::get);
    for (int i = 0; i < 130; i++) log.record(DiagnosticLog.Event.TIMER_STARTED, null);
    assertEquals(100, log.report().split("TIMER_STARTED", -1).length - 1);
    assertTrue(file.length() <= 65536);
    now.addAndGet(8L * 24 * 60 * 60 * 1000);
    assertFalse(new DiagnosticLog(file, now::get).report().contains("TIMER_STARTED"));
    log.record(DiagnosticLog.Event.TIMER_CANCELLED, null);
    log.clear();
    assertFalse(file.exists());
    assertFalse(log.report().contains("TIMER_CANCELLED"));
  }

  @Test
  public void unavailableStorageNeverEscapesIntoAppOperation() throws Exception {
    File parent = folder.newFile("not-a-directory");
    DiagnosticLog log = new DiagnosticLog(new File(parent, "log.json"));
    log.record(DiagnosticLog.Event.LOCAL_FAILURE, new IOException("secret"));
    log.report();
    log.clear();
  }
}
