package com.babybuddypocket.app;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.List;

/** Small platform-view toolkit. No font downloads, icon packages or UI dependencies. */
final class Ui {
  final Activity activity;
  final boolean dark;
  final int background, surface, ink, muted, accent, soft, line;

  Ui(Activity activity) {
    this.activity = activity;
    dark =
        (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    background = color(dark ? "#161B18" : "#F4F5F3");
    surface = color(dark ? "#232B26" : "#FFFFFF");
    ink = color(dark ? "#F0F3ED" : "#24382D");
    muted = color(dark ? "#ACB8AF" : "#6A776F");
    accent = color(dark ? "#AED1BC" : "#456D57");
    soft = color(dark ? "#314A3C" : "#E2EEE4");
    line = color(dark ? "#3B453F" : "#E3E8E2");
  }

  static int color(String hex) {
    return Color.parseColor(hex);
  }

  int dp(float value) {
    return (int) (value * activity.getResources().getDisplayMetrics().density + .5f);
  }

  LinearLayout column() {
    LinearLayout v = new LinearLayout(activity);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  LinearLayout row() {
    LinearLayout v = new LinearLayout(activity);
    v.setOrientation(LinearLayout.HORIZONTAL);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  void clickable(View v, Runnable action) {
    v.setClickable(true);
    v.setFocusable(true);
    v.setOnClickListener(w -> action.run());
    v.setForeground(
        new RippleDrawable(
            ColorStateList.valueOf(dark ? 0x20FFFFFF : 0x16000000), null, shape(Color.WHITE, 20)));
  }

  TextView text(String value, int size, int color, boolean bold) {
    TextView t = new TextView(activity);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setFontFeatureSettings("kern");
    if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    t.setLineSpacing(dp(2), 1);
    return t;
  }

  TextView text(String value, int size) {
    return text(value, size, ink, false);
  }

  void add(LinearLayout parent, View child) {
    parent.addView(child, new LinearLayout.LayoutParams(-1, -2));
  }

  void gap(LinearLayout parent, int size) {
    View v = new View(activity);
    parent.addView(v, new LinearLayout.LayoutParams(1, dp(size)));
  }

  LinearLayout card(LinearLayout parent, int padding) {
    LinearLayout c = column();
    c.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
    c.setBackground(shape(surface, 24));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(12);
    parent.addView(c, p);
    return c;
  }

  Button button(String label, boolean filled, Runnable action) {
    Button b = new Button(activity);
    b.setStateListAnimator(null);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextSize(15);
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setTextColor(filled ? (dark ? background : Color.WHITE) : accent);
    b.setMinHeight(dp(50));
    b.setMinimumHeight(dp(50));
    b.setPadding(dp(16), dp(10), dp(16), dp(10));
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x22FFFFFF), shape(filled ? accent : soft, 18), null));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  EditText edit(String hint) {
    EditText edit = new EditText(activity);
    edit.setTextSize(16);
    edit.setTextColor(ink);
    edit.setHintTextColor(muted);
    edit.setHint(hint);
    edit.setPadding(dp(14), dp(12), dp(14), dp(12));
    edit.setMinHeight(dp(52));
    GradientDrawable bg = shape(background, 14);
    bg.setStroke(dp(1), line);
    edit.setBackground(bg);
    return edit;
  }

  Spinner choice(TextView label, List<String> choices, int selected) {
    Spinner spinner = new Spinner(activity, Spinner.MODE_DROPDOWN);
    spinner.setId(View.generateViewId());
    label.setLabelFor(spinner.getId());
    spinner.setMinimumHeight(dp(52));
    GradientDrawable normal = shape(background, 14), focused = shape(background, 14);
    normal.setStroke(dp(1), line);
    focused.setStroke(dp(1), accent);
    StateListDrawable border = new StateListDrawable();
    border.addState(new int[] {android.R.attr.state_focused}, focused);
    border.addState(new int[] {android.R.attr.state_pressed}, focused);
    border.addState(new int[] {}, normal);
    spinner.setBackground(border);
    spinner.setBackgroundTintList(null);
    spinner.setPadding(0, 0, 0, 0);
    LayerDrawable popup = new LayerDrawable(new Drawable[] {shape(surface, 18)});
    popup.setPadding(dp(6), dp(6), dp(6), dp(6));
    spinner.setPopupBackgroundDrawable(popup);
    spinner.setDropDownWidth(ViewGroup.LayoutParams.MATCH_PARENT);
    spinner.setDropDownVerticalOffset(dp(6));
    spinner.setAdapter(
        new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, choices) {
          @Override
          public View getView(int position, View recycled, ViewGroup parent) {
            TextView value = recycled instanceof TextView ? (TextView) recycled : text("", 16);
            value.setText(getItem(position));
            value.setTextColor("Choose…".equals(getItem(position)) ? muted : ink);
            value.setGravity(Gravity.CENTER_VERTICAL);
            value.setMinHeight(dp(52));
            value.setPaddingRelative(dp(14), dp(12), dp(14), dp(12));
            Drawable arrow = activity.getDrawable(R.drawable.ic_dropdown_arrow).mutate();
            arrow.setTint(muted);
            value.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrow, null);
            value.setCompoundDrawablePadding(dp(12));
            return value;
          }

          @Override
          public View getDropDownView(int position, View recycled, ViewGroup parent) {
            CheckedTextView row =
                recycled instanceof CheckedTextView
                    ? (CheckedTextView) recycled
                    : new CheckedTextView(activity) {
                      @Override
                      public void setHorizontallyScrolling(boolean enabled) {
                        // Android's dropdown list forces this on after binding each text row.
                        super.setHorizontallyScrolling(false);
                      }
                    };
            boolean chosen = position == spinner.getSelectedItemPosition();
            row.setText(getItem(position));
            row.setTextSize(16);
            row.setTextColor(chosen ? accent : ink);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setSingleLine(false);
            row.setPaddingRelative(dp(12), dp(12), dp(12), dp(12));
            row.setBackground(shape(chosen ? soft : surface, 12));
            row.setCheckMarkDrawable(R.drawable.ic_choice_check);
            row.setMinHeight(dp(48));
            row.setCheckMarkTintList(
                new ColorStateList(
                    new int[][] {{android.R.attr.state_checked}, {}},
                    new int[] {accent, Color.TRANSPARENT}));
            row.setChecked(chosen);
            return row;
          }
        });
    spinner.setSelection(selected);
    return spinner;
  }

  void section(LinearLayout parent, String label) {
    gap(parent, 12);
    TextView t = text(label, 12, muted, true);
    t.setLetterSpacing(.09f);
    add(parent, t);
    gap(parent, 10);
  }

  int tone(String type) {
    switch (type) {
      case "feedings":
        return color(dark ? "#9FD7B3" : "#278C5A");
      case "sleep":
        return color(dark ? "#B4B8F1" : "#696BC1");
      case "changes":
        return color(dark ? "#D7BE84" : "#96753B");
      case "tummy-times":
        return color(dark ? "#F1BD85" : "#C17C34");
      case "pumping":
        return color(dark ? "#E4A6BE" : "#B46B86");
      default:
        return accent;
    }
  }

  int tint(String type) {
    int c = tone(type);
    return Color.rgb(
        (Color.red(c) + (dark ? 32 : 255) * 5) / 6,
        (Color.green(c) + (dark ? 40 : 255) * 5) / 6,
        (Color.blue(c) + (dark ? 35 : 255) * 5) / 6);
  }

  View icon(String type, int size, boolean badge) {
    return new Icon(type, size, badge);
  }

  final class Icon extends View {
    private final String type;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean badge;
    private final Path moon = new Path();

    Icon(String type, int size, boolean badge) {
      super(activity);
      this.type = type;
      this.badge = badge;
      moon.moveTo(20, 15);
      moon.cubicTo(11, 18, 5, 10, 10, 3);
      moon.cubicTo(2, 5, 0, 14, 6, 19);
      moon.cubicTo(12, 24, 20, 21, 20, 15);
      moon.close();
      setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float w = getWidth(), h = getHeight();
      if (badge) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tint(type));
        canvas.drawRoundRect(0, 0, w, h, dp(15), dp(15), paint);
      }
      canvas.save();
      float scale = Math.min(w, h) / (badge ? 48f : 28f);
      canvas.translate((w - 24 * scale) / 2, (h - 24 * scale) / 2);
      canvas.scale(scale, scale);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(1.8f);
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeJoin(Paint.Join.ROUND);
      paint.setColor(tone(type));
      switch (type) {
        case "feedings":
        case "pumping":
          canvas.drawRoundRect(7, 8, 17, 22, 3, 3, paint);
          canvas.drawRect(8, 5, 16, 8, paint);
          canvas.drawRoundRect(10, 1, 14, 5, 2, 2, paint);
          canvas.drawLine(13, 12, 16, 12, paint);
          canvas.drawLine(13, 16, 16, 16, paint);
          break;
        case "sleep":
          canvas.drawPath(moon, paint);
          break;
        case "changes":
          canvas.drawRoundRect(2, 5, 22, 20, 5, 5, paint);
          canvas.drawLine(2, 9, 8, 10, paint);
          canvas.drawLine(22, 9, 16, 10, paint);
          path(canvas, new float[] {3, 12, 7, 14, 9, 20});
          path(canvas, new float[] {21, 12, 17, 14, 15, 20});
          break;
        case "tummy-times":
          canvas.drawCircle(12, 12, 9, paint);
          canvas.drawPoint(9, 10, paint);
          canvas.drawPoint(15, 10, paint);
          canvas.drawArc(7, 8, 17, 17, 20, 140, false, paint);
          break;
        case "today":
          path(canvas, new float[] {2, 11, 12, 2, 22, 11, 19, 11, 19, 22, 5, 22, 5, 11});
          canvas.drawRect(10, 15, 14, 22, paint);
          break;
        case "timeline":
          for (int y = 5; y <= 20; y += 7) {
            canvas.drawPoint(3, y, paint);
            canvas.drawLine(8, y, 22, y, paint);
          }
          break;
        case "trends":
          canvas.drawLine(2, 22, 22, 22, paint);
          canvas.drawRoundRect(4, 13, 7, 22, 1, 1, paint);
          canvas.drawRoundRect(11, 8, 14, 22, 1, 1, paint);
          canvas.drawRoundRect(18, 2, 21, 22, 1, 1, paint);
          break;
        case "settings":
          canvas.drawCircle(12, 12, 7, paint);
          canvas.drawCircle(12, 12, 2.5f, paint);
          for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            canvas.drawLine(
                12 + (float) Math.cos(a) * 8,
                12 + (float) Math.sin(a) * 8,
                12 + (float) Math.cos(a) * 10,
                12 + (float) Math.sin(a) * 10,
                paint);
          }
          break;
        case "timers":
          canvas.drawCircle(12, 13, 8, paint);
          canvas.drawLine(9, 1, 15, 1, paint);
          canvas.drawLine(12, 5, 12, 1, paint);
          path(canvas, new float[] {12, 8, 12, 13, 16, 15});
          break;
        case "notes":
          canvas.drawRoundRect(4, 2, 20, 22, 3, 3, paint);
          canvas.drawLine(8, 8, 16, 8, paint);
          canvas.drawLine(8, 12, 16, 12, paint);
          canvas.drawLine(8, 16, 13, 16, paint);
          break;
        default:
          canvas.drawLine(12, 4, 12, 20, paint);
          canvas.drawLine(4, 12, 20, 12, paint);
      }
      canvas.restore();
    }

    private void path(Canvas c, float[] points) {
      Path p = new Path();
      p.moveTo(points[0], points[1]);
      for (int i = 2; i < points.length; i += 2) p.lineTo(points[i], points[i + 1]);
      c.drawPath(p, paint);
    }
  }
}
