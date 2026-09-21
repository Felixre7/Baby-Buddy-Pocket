package com.babybuddypocket.app;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Small framework-camera preview; no external scanner app or Play Services required. */
@SuppressWarnings("deprecation")
public final class QrScanActivity extends Activity implements SurfaceHolder.Callback {
  private static final int CAMERA_PERMISSION = 7;
  private final ExecutorService decoder = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private Camera camera;
  private SurfaceView preview;
  private FrameLayout frame;
  private TextView status;
  private Button permission, torch;
  private Ui ui;
  private boolean resumed, surfaceReady, finished;
  private int generation, previewWidth, previewHeight;
  private long nextDecode;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow()
        .addFlags(
            WindowManager.LayoutParams.FLAG_SECURE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    ui = new Ui(this);
    LinearLayout root = ui.column();
    root.setBackgroundColor(ui.background);
    root.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(16));
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets i =
                insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            root.setPadding(
                i.left + ui.dp(20), i.top + ui.dp(20), i.right + ui.dp(20), i.bottom + ui.dp(16));
          } else root.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(16));
          return insets;
        });
    ui.add(root, ui.text("Scan Baby Buddy code", 24, ui.ink, true));
    ui.add(
        root,
        ui.text(
            "Open Baby Buddy in your browser, then choose Add a device. Point your camera at its"
                + " login QR code.",
            14,
            ui.muted,
            false));
    frame = new FrameLayout(this);
    frame.setBackgroundColor(0xFF101612);
    root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
    preview = new SurfaceView(this);
    frame.addView(preview, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
    preview.getHolder().addCallback(this);
    frame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitPreview());
    status = ui.text("Camera permission is needed only to scan a code.", 14, ui.muted, false);
    ui.add(root, status);
    permission =
        ui.button(
            "Allow camera",
            false,
            () -> {
              if (checkSelfPermission(Manifest.permission.CAMERA)
                  == PackageManager.PERMISSION_GRANTED) startCamera();
              else if (getPreferences(0).getBoolean("asked", false)
                  && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                startActivity(
                    new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + getPackageName())));
              } else requestCamera();
            });
    ui.add(root, permission);
    torch = ui.button("Turn light on", false, this::toggleTorch);
    torch.setVisibility(View.GONE);
    ui.add(root, torch);
    ui.add(root, ui.button("Enter details manually", false, this::finish));
    if (state == null
        && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        && !getPreferences(0).getBoolean("asked", false)) requestCamera();
  }

  private void requestCamera() {
    getPreferences(0).edit().putBoolean("asked", true).apply();
    requestPermissions(new String[] {Manifest.permission.CAMERA}, CAMERA_PERMISSION);
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == CAMERA_PERMISSION) {
      if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startCamera();
      else {
        status.setText(R.string.camera_denied);
        permission.setText(R.string.camera_settings);
        permission.setVisibility(View.VISIBLE);
      }
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    startCamera();
  }

  @Override
  protected void onPause() {
    resumed = false;
    stopCamera();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    decoder.shutdownNow();
    super.onDestroy();
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    surfaceReady = true;
    startCamera();
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    fitPreview();
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    surfaceReady = false;
    stopCamera();
  }

  private void startCamera() {
    if (!resumed
        || !surfaceReady
        || finished
        || camera != null
        || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
      return;
    try {
      int id = -1;
      Camera.CameraInfo info = new Camera.CameraInfo();
      for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
        Camera.getCameraInfo(i, info);
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
          id = i;
          break;
        }
      }
      if (id < 0) throw new IllegalStateException();
      camera = Camera.open(id);
      Camera.Parameters params = camera.getParameters();
      Camera.Size size =
          params.getSupportedPreviewSizes().stream()
              .filter(s -> s.width <= 1280 && s.height <= 1280)
              .max(Comparator.comparingInt(s -> s.width * s.height))
              .orElse(params.getPreviewSize());
      params.setPreviewSize(size.width, size.height);
      params.setPreviewFormat(ImageFormat.NV21);
      List<String> focus = params.getSupportedFocusModes();
      if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      camera.setParameters(params);
      int degrees = getWindowManager().getDefaultDisplay().getRotation() * 90;
      int orientation = (info.orientation - degrees + 360) % 360;
      camera.setDisplayOrientation(orientation);
      previewWidth = orientation % 180 == 0 ? size.width : size.height;
      previewHeight = orientation % 180 == 0 ? size.height : size.width;
      fitPreview();
      camera.setPreviewDisplay(preview.getHolder());
      int activeGeneration = ++generation;
      camera.setPreviewCallback(
          (data, source) -> {
            if (!resumed
                || generation != activeGeneration
                || SystemClock.elapsedRealtime() < nextDecode
                || finished) return;
            nextDecode = Long.MAX_VALUE;
            byte[] pixels = data.clone();
            decoder.execute(
                () -> {
                  String raw = QrDecoder.decode(pixels, size.width, size.height);
                  main.post(
                      () -> {
                        if (generation != activeGeneration || !resumed || finished) return;
                        nextDecode = SystemClock.elapsedRealtime() + 200;
                        if (raw != null) acceptCode(raw);
                      });
                });
          });
      camera.startPreview();
      nextDecode = 0;
      permission.setVisibility(View.GONE);
      List<String> flash = params.getSupportedFlashModes();
      torch.setVisibility(
          flash != null && flash.contains(Camera.Parameters.FLASH_MODE_TORCH)
              ? View.VISIBLE
              : View.GONE);
      torch.setText(R.string.light_on);
      status.setText(R.string.camera_ready);
    } catch (Exception e) {
      stopCamera();
      status.setText(R.string.camera_unavailable);
      permission.setText(R.string.camera_retry);
      permission.setVisibility(View.VISIBLE);
    }
  }

  private void fitPreview() {
    if (previewWidth <= 0 || previewHeight <= 0 || frame.getWidth() == 0 || frame.getHeight() == 0)
      return;
    float scale =
        Math.min(
            (float) frame.getWidth() / previewWidth, (float) frame.getHeight() / previewHeight);
    int width = Math.round(previewWidth * scale), height = Math.round(previewHeight * scale);
    if (preview.getLayoutParams().width != width || preview.getLayoutParams().height != height)
      preview.setLayoutParams(new FrameLayout.LayoutParams(width, height, Gravity.CENTER));
  }

  void acceptCode(String raw) {
    try {
      DeviceCode code = DeviceCode.parse(raw);
      finished = true;
      stopCamera();
      setResult(
          RESULT_OK, new Intent().putExtra("server", code.server).putExtra("token", code.token));
      finish();
    } catch (IllegalArgumentException e) {
      status.setText(e.getMessage());
      nextDecode = SystemClock.elapsedRealtime() + 1500;
    }
  }

  private void toggleTorch() {
    if (camera == null) return;
    try {
      Camera.Parameters params = camera.getParameters();
      boolean on = !Camera.Parameters.FLASH_MODE_TORCH.equals(params.getFlashMode());
      params.setFlashMode(
          on ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
      camera.setParameters(params);
      torch.setText(on ? R.string.light_off : R.string.light_on);
    } catch (RuntimeException ignored) {
      status.setText(R.string.light_unavailable);
    }
  }

  private void stopCamera() {
    generation++;
    if (camera != null) {
      camera.setPreviewCallback(null);
      try {
        camera.stopPreview();
      } catch (RuntimeException ignored) {
      }
      camera.release();
      camera = null;
    }
    if (torch != null) torch.setVisibility(View.GONE);
  }
}
