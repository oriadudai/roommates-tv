package com.dudai.roommates;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The apartment home screen: four animals on the moon, weather with sunrise and sunset,
 * a daily rotating widget (word / meal / animal fact / joke) and app tiles.
 *
 * Everything sits on a fixed 1280x720 "stage" (the mockup size) that is scaled to the real screen.
 */
public class MainActivity extends Activity {

    // ---------- Settings you may want to change ----------

    /** Weather location: Haifa. */
    private static final double LAT = 32.79, LON = 34.99;

    private static final String NAMES = "אוריה · הדס · נועה · נעם";

    /** Tiles, right to left. Packages are tried first; if none is installed the app is found by name. */
    private final AppTarget[] apps = {
            new AppTarget("Netflix", 0xFFFF8FC7,
                    new String[]{"com.netflix.ninja", "com.netflix.mediaclient"}, new String[]{"netflix"}),
            new AppTarget("סלקום TV", 0xFFB79CFF,
                    new String[]{}, new String[]{"סלקום", "cellcom"}),
            new AppTarget("Disney+", 0xFF8CCBFF,
                    new String[]{"com.disney.disneyplus"}, new String[]{"disney"}),
            new AppTarget("HBO Max", 0xFF7FE3C4,
                    new String[]{"com.wbd.stream", "com.hbo.hbonow"}, new String[]{"hbo", "=max"}),
            AppTarget.hdmi("HDMI", 0xFFFFB3C7),
            AppTarget.allApps("כל האפליקציות", 0xFFFFD58A),
    };

    /** Which HDMI port the HDMI tile opens: 0 = the first HDMI input the TV reports, 1 = the second, ... */
    private static final int HDMI_INDEX = 0;

    // ---------- Design ----------

    private static final int W = 1280, H = 720;
    private static final int BG = 0xFFE7DAFF, BLOB_A = 0xFFFFC6E0, BLOB_B = 0xFFC7E6FF;
    private static final int INK = 0xFF3A1F5C, INK_SOFT = 0xFF5B4378;
    private static final int CARD = 0xC8FFFFFF;

    private static final int TILE_W = 170, TILE_H = 125, TILE_GAP = 29;
    private static final float TILE_FOCUS_SCALE = 1.15f;
    private static final int CARD_SECONDS = 9, JOKE_ANSWER_SECONDS = 4;

    private static final Locale HEBREW = Locale.forLanguageTag("he");

    // ---------- State ----------

    private final List<Animator> loops = new ArrayList<>();
    private final List<View> tileViews = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService background = Executors.newSingleThreadExecutor();

    private FrameLayout stage;
    private Typeface varela, fredoka;

    private TextView dateView, greetingView;
    private ImageView weatherIcon;
    private TextView tempView, weatherText, sunriseView, sunsetView;

    private LinearLayout cardContent;
    private TextView chipView, cardTitle, cardBody, cardExtra;
    private final List<View> dots = new ArrayList<>();
    private List<DailyContent.Card> cards = new ArrayList<>();
    private int cardIndex = 0;
    private long cardsDay = -1;

    private FrameLayout drawer;

    static class AppTarget {
        final String name;
        final int color;
        final String[] packages;
        final String[] keywords;
        final boolean allApps;
        final boolean hdmi;

        AppTarget(String name, int color, String[] packages, String[] keywords) {
            this(name, color, packages, keywords, false, false);
        }

        private AppTarget(String name, int color, String[] packages, String[] keywords,
                          boolean allApps, boolean hdmi) {
            this.name = name;
            this.color = color;
            this.packages = packages;
            this.keywords = keywords;
            this.allApps = allApps;
            this.hdmi = hdmi;
        }

        static AppTarget allApps(String name, int color) {
            return new AppTarget(name, color, new String[]{}, new String[]{}, true, false);
        }

        static AppTarget hdmi(String name, int color) {
            return new AppTarget(name, color, new String[]{}, new String[]{}, false, true);
        }
    }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        varela = getResources().getFont(R.font.varela);
        fredoka = getResources().getFont(R.font.fredoka);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        stage = new FrameLayout(this);
        stage.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.addView(stage, new FrameLayout.LayoutParams(W, H, Gravity.TOP | Gravity.LEFT));
        setContentView(root);

        buildBackground();
        buildScene();
        buildWeather();
        buildClockAndGreeting();
        buildDailyCard();
        buildTiles();

        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitStage(root));
        root.post(() -> tileViews.get(0).requestFocus());
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (Animator a : loops) a.resume();
        minuteTick.run();
        weatherTick.run();
        handler.postDelayed(cardTick, CARD_SECONDS * 1000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (Animator a : loops) a.pause();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        background.shutdownNow();
    }

    /** Back closes the app drawer; otherwise there is nowhere to go back to. */
    @Override
    public void onBackPressed() {
        if (drawer != null) closeDrawer();
    }

    private void fitStage(View root) {
        if (root.getWidth() == 0 || root.getHeight() == 0) return;
        float s = Math.min(root.getWidth() / (float) W, root.getHeight() / (float) H);
        stage.setPivotX(0);
        stage.setPivotY(0);
        stage.setScaleX(s);
        stage.setScaleY(s);
        stage.setTranslationX((root.getWidth() - W * s) / 2f);
        stage.setTranslationY((root.getHeight() - H * s) / 2f);
    }

    // =====================================================================
    // Background and the moon scene
    // =====================================================================

    private void buildBackground() {
        View a = add(glowBlob(BLOB_A, 310), 780, 360, 620, 620);
        View b = add(glowBlob(BLOB_B, 280), 200, -240, 560, 560);
        drift(a, 40, -30, 4500);
        drift(b, -40, 30, 6000);

        ImageView c1 = image(R.drawable.cloud);
        add(c1, 640, 150, 200, 70).setAlpha(0.9f);
        pingPong(ObjectAnimator.ofFloat(c1, View.TRANSLATION_X, 0f, 60f), 8000, 0);

        int[][] sparkles = {   // x, y, size, drawable index, period, delay
                {620, 520, 30, 0, 2200, 0}, {30, 300, 24, 1, 2800, 600},
                {610, 120, 26, 2, 1900, 1100}, {1010, 140, 22, 0, 2400, 300},
        };
        int[] res = {R.drawable.sparkle, R.drawable.sparkle_yellow, R.drawable.sparkle_pink};
        for (int[] s : sparkles) {
            ImageView sp = image(res[s[3]]);
            sp.setAlpha(0.2f);
            add(sp, s[0], s[1], s[2], s[2]);
            ObjectAnimator tw = ObjectAnimator.ofPropertyValuesHolder(sp,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0.2f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 0.6f, 1.1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.6f, 1.1f),
                    PropertyValuesHolder.ofFloat(View.ROTATION, 0f, 45f));
            pingPong(tw, s[4] / 2, s[5]);
        }
    }

    /** Lion (Auriah), capybara (Noa), dog (Noam) and rabbit (Hadas) on a swinging moon. */
    private void buildScene() {
        final int sw = 560, sh = 300;
        FrameLayout scene = new FrameLayout(this);
        ImageView lionTail = image(R.drawable.scene_lion_tail);
        ImageView dogTail = image(R.drawable.scene_dog_tail);
        ImageView ear = image(R.drawable.scene_rabbit_ear);
        ImageView body = image(R.drawable.scene_base);
        ImageView eyesA = image(R.drawable.scene_eyes_a);
        ImageView eyesB = image(R.drawable.scene_eyes_b);
        for (View v : new View[]{lionTail, dogTail, ear, body, eyesA, eyesB}) {
            scene.addView(v, new FrameLayout.LayoutParams(sw, sh));
        }

        // Names under the animals (positions from the mockup)
        String[] names = {"אוריה", "נועה", "נעם", "הדס"};
        int[] nameX = {50, 175, 305, 430};
        for (int i = 0; i < names.length; i++) {
            TextView tag = text(names[i], varela, 18, 0, INK);
            tag.setGravity(Gravity.CENTER);
            tag.setBackground(rounded(0xFFFFFFFF, 20));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(80, 30, Gravity.TOP | Gravity.LEFT);
            lp.leftMargin = nameX[i];
            lp.topMargin = 262;
            scene.addView(tag, lp);
        }
        add(scene, 56, 210, sw, sh);

        scene.setPivotX(sw / 2f);
        scene.setPivotY(0);
        pingPong(ObjectAnimator.ofFloat(scene, View.ROTATION, -2f, 2f), 2000, 0);

        pivot(lionTail, 122, 236);
        pingPong(ObjectAnimator.ofFloat(lionTail, View.ROTATION, -12f, 14f), 700, 0);
        pivot(dogTail, 376, 234);
        pingPong(ObjectAnimator.ofFloat(dogTail, View.ROTATION, -18f, 18f), 250, 0);

        pivot(ear, 482, 162);
        ValueAnimator twitch = loop(3000, 0);
        twitch.addUpdateListener(an -> {
            float f = (float) an.getAnimatedValue();
            ear.setRotation(f > 0.8f && f < 0.96f ? 14f * (float) Math.sin((f - 0.8f) / 0.16f * Math.PI) : 0f);
        });

        blink(eyesA, 171, 4500, 0);
        blink(eyesB, 179, 5200, 1000);
    }

    private void blink(View eyes, float pivotY, int period, int delay) {
        eyes.setPivotX(280);
        eyes.setPivotY(pivotY);
        ValueAnimator a = loop(period, delay);
        a.addUpdateListener(an -> {
            float d = Math.abs((float) an.getAnimatedValue() - 0.94f) / 0.03f;
            eyes.setScaleY(d < 1f ? 0.1f + 0.9f * d : 1f);
        });
    }

    // =====================================================================
    // Weather, clock and greeting
    // =====================================================================

    private void buildWeather() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setPadding(24, 18, 24, 18);
        card.setBackground(rounded(CARD, 30));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = image(R.drawable.wx_sun);
        top.addView(weatherIcon, new LinearLayout.LayoutParams(64, 64));

        LinearLayout temps = new LinearLayout(this);
        temps.setOrientation(LinearLayout.VERTICAL);
        temps.setPadding(16, 0, 0, 0);
        tempView = text("--°", fredoka, 52, 700, INK);
        tempView.setIncludeFontPadding(false);
        weatherText = text("טוען...", varela, 18, 0, INK);
        temps.addView(tempView);
        temps.addView(weatherText);
        top.addView(temps);
        card.addView(top);

        LinearLayout sun = new LinearLayout(this);
        sun.setOrientation(LinearLayout.HORIZONTAL);
        sun.setGravity(Gravity.CENTER_VERTICAL);
        sun.setPadding(0, 12, 0, 0);
        sunriseView = text("--:--", varela, 18, 0, INK);
        sunsetView = text("--:--", varela, 18, 0, INK);
        addSunItem(sun, R.drawable.ic_sunrise, "זריחה", sunriseView);
        View gap = new View(this);
        sun.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));
        addSunItem(sun, R.drawable.ic_sunset, "שקיעה", sunsetView);
        card.addView(sun, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        add(card, 56, 40, 320, 160);
    }

    private void addSunItem(LinearLayout row, int icon, String label, TextView time) {
        row.addView(image(icon), new LinearLayout.LayoutParams(24, 24));
        TextView l = text(label + " ", varela, 18, 0, INK);
        l.setPadding(6, 0, 0, 0);
        row.addView(l);
        row.addView(time);
    }

    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            handler.removeCallbacks(this);
            background.execute(() -> {
                try {
                    Weather w = Weather.fetch(LAT, LON);
                    handler.post(() -> showWeather(w));
                    handler.postDelayed(this, 30 * 60_000L);       // every 30 minutes
                } catch (Exception e) {
                    handler.post(() -> weatherText.setText("אין חיבור"));
                    handler.postDelayed(this, 5 * 60_000L);        // try again soon
                }
            });
        }
    };

    private void showWeather(Weather w) {
        tempView.setText(w.temperature + "°");
        weatherText.setText(w.description());
        weatherIcon.setImageResource(w.icon());
        sunriseView.setText(w.sunrise);
        sunsetView.setText(w.sunset);
    }

    private void buildClockAndGreeting() {
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        style(clock, fredoka, 80, 700, INK);
        clock.setIncludeFontPadding(false);
        addRight(clock, 56, 40);

        dateView = text("", varela, 24, 0, INK);
        addRight(dateView, 56, 128);

        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setGravity(Gravity.CENTER_HORIZONTAL);
        greetingView = text("", varela, 34, 0, INK);
        greetingView.setGravity(Gravity.CENTER);
        TextView names = text(NAMES, varela, 22, 0, INK_SOFT);
        names.setGravity(Gravity.CENTER);
        g.addView(greetingView);
        g.addView(names);
        add(g, 400, 58, 480, 110);
    }

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            dateView.setText(new SimpleDateFormat("EEEE, d בMMMM", HEBREW).format(now));
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            greetingView.setText(hour >= 5 && hour < 12 ? "בוקר טוב!"
                    : hour < 17 && hour >= 12 ? "צהריים טובים!"
                    : hour >= 17 && hour < 22 ? "ערב טוב!" : "לילה טוב!");

            long day = java.time.LocalDate.now().toEpochDay();
            if (day != cardsDay) {          // new day: new word, meal, fact and joke
                cardsDay = day;
                cards = DailyContent.today(MainActivity.this);
                cardIndex = 0;
                showCard(false);
            }
            handler.removeCallbacks(this);
            handler.postDelayed(this, 60_000);
        }
    };

    // =====================================================================
    // The rotating daily widget
    // =====================================================================

    private void buildDailyCard() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(rounded(0xC8FFFFFF, 36));
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        cardContent.setPadding(34, 28, 34, 0);

        chipView = text("", varela, 18, 0, INK);
        chipView.setPadding(16, 6, 16, 6);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.gravity = Gravity.START;
        cardContent.addView(chipView, chipLp);

        cardTitle = text("", varela, 40, 0, INK);
        cardTitle.setPadding(0, 14, 0, 0);
        cardTitle.setLineSpacing(0, 1.1f);
        cardBody = text("", varela, 24, 0, INK_SOFT);
        cardBody.setPadding(0, 10, 0, 0);
        cardBody.setLineSpacing(0, 1.2f);
        cardExtra = text("", varela, 22, 0, INK_SOFT);
        cardExtra.setPadding(0, 8, 0, 0);
        cardExtra.setLineSpacing(0, 1.2f);
        for (TextView t : new TextView[]{cardTitle, cardBody, cardExtra}) {
            cardContent.addView(t, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        card.addView(cardContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 250, Gravity.TOP));

        LinearLayout dotRow = new LinearLayout(this);
        dotRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(14, 14);
            lp.setMarginEnd(10);
            dotRow.addView(d, lp);
            dots.add(d);
        }
        FrameLayout.LayoutParams dotsLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 14, Gravity.BOTTOM | Gravity.START);
        dotsLp.setMarginStart(34);
        dotsLp.bottomMargin = 24;
        card.addView(dotRow, dotsLp);

        add(card, 1280 - 56 - 560, 180, 560, 300);
    }

    private final Runnable cardTick = new Runnable() {
        @Override
        public void run() {
            if (!cards.isEmpty()) {
                cardIndex = (cardIndex + 1) % cards.size();
                showCard(true);
            }
            handler.postDelayed(this, CARD_SECONDS * 1000L);
        }
    };

    private final Runnable revealAnswer = () -> cardExtra.animate().alpha(1f).setDuration(500).start();

    private void showCard(boolean animate) {
        if (cards.isEmpty()) return;
        DailyContent.Card c = cards.get(cardIndex);
        Runnable fill = () -> {
            chipView.setText(c.chip);
            chipView.setBackground(rounded(c.chipColor, 20));
            cardTitle.setText(c.title);
            cardTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.titleSize);
            cardTitle.setTextDirection(c.english ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
            cardTitle.setTextAlignment(c.english ? View.TEXT_ALIGNMENT_VIEW_START : View.TEXT_ALIGNMENT_TEXT_START);
            setOrHide(cardBody, c.body);
            setOrHide(cardExtra, c.extra);
            cardExtra.setTextDirection(c.english ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
            cardExtra.setTextAlignment(c.english ? View.TEXT_ALIGNMENT_VIEW_START : View.TEXT_ALIGNMENT_TEXT_START);
            handler.removeCallbacks(revealAnswer);
            if (c.revealExtra) {
                cardExtra.setAlpha(0f);
                handler.postDelayed(revealAnswer, JOKE_ANSWER_SECONDS * 1000L);
            } else {
                cardExtra.setAlpha(1f);
            }
            for (int i = 0; i < dots.size(); i++) {
                boolean on = i == cardIndex;
                dots.get(i).setBackground(rounded(on ? INK : 0xFFC9B6E8, 7));
                ViewGroup.LayoutParams lp = dots.get(i).getLayoutParams();
                lp.width = on ? 36 : 14;
                dots.get(i).setLayoutParams(lp);
            }
        };
        if (!animate) {
            fill.run();
            return;
        }
        cardContent.animate().alpha(0f).translationY(-10).setDuration(250).withEndAction(() -> {
            fill.run();
            cardContent.setTranslationY(12);
            cardContent.animate().alpha(1f).translationY(0).setDuration(400).start();
        }).start();
    }

    private void setOrHide(TextView t, String s) {
        t.setText(s);
        t.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // =====================================================================
    // App tiles
    // =====================================================================

    private void buildTiles() {
        int y = H - 44 - TILE_H;
        for (int i = 0; i < apps.length; i++) {
            AppTarget app = apps[i];
            int x = W - 56 - TILE_W - i * (TILE_W + TILE_GAP);

            GradientDrawable bg = rounded(app.color, 34);
            LinearLayout v = new LinearLayout(this);
            v.setOrientation(LinearLayout.VERTICAL);
            v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            v.setPadding(16, 16, 16, 14);
            v.setBackground(bg);
            v.setFocusable(true);
            v.setFocusableInTouchMode(true);
            v.setClickable(true);
            v.setId(View.generateViewId());
            v.setPivotX(TILE_W / 2f);
            v.setPivotY(TILE_H);

            int icon = app.allApps ? R.drawable.ic_grid : app.hdmi ? R.drawable.ic_hdmi : R.drawable.ic_play;
            v.addView(image(icon), new LinearLayout.LayoutParams(38, 38));
            v.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
            TextView name = text(app.name, varela, app.allApps ? 19 : 24, 0, INK);
            name.setSingleLine(true);
            name.setIncludeFontPadding(false);
            v.addView(name);

            add(v, x, y, TILE_W, TILE_H);
            tileViews.add(v);

            final ObjectAnimator[] bounce = new ObjectAnimator[1];
            v.setOnFocusChangeListener((view, focused) -> {
                if (focused) {
                    view.animate().scaleX(TILE_FOCUS_SCALE).scaleY(TILE_FOCUS_SCALE).setDuration(200).start();
                    bg.setStroke(6, 0xFFFFFFFF);
                    view.setElevation(12);
                    bounce[0] = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -10f);
                    bounce[0].setDuration(550);
                    bounce[0].setRepeatCount(ValueAnimator.INFINITE);
                    bounce[0].setRepeatMode(ValueAnimator.REVERSE);
                    bounce[0].start();
                } else {
                    if (bounce[0] != null) bounce[0].cancel();
                    view.animate().scaleX(1f).scaleY(1f).translationY(0).setDuration(200).start();
                    bg.setStroke(0, 0);
                    view.setElevation(0);
                }
            });
            v.setOnClickListener(view -> {
                if (app.allApps) openDrawer();
                else if (app.hdmi) openHdmi();
                else launch(app);
            });
        }
        for (int i = 0; i < tileViews.size(); i++) {
            View v = tileViews.get(i);
            v.setNextFocusLeftId(tileViews.get(Math.min(i + 1, tileViews.size() - 1)).getId());
            v.setNextFocusRightId(tileViews.get(Math.max(i - 1, 0)).getId());
            v.setNextFocusUpId(v.getId());
            v.setNextFocusDownId(v.getId());
        }
    }

    private void launch(AppTarget app) {
        Intent intent = findLaunchIntent(app);
        if (intent == null) {
            toast("לא מצאתי את " + app.name + " בטלוויזיה");
            return;
        }
        startApp(intent, app.name);
    }

    private void startApp(Intent intent, String name) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            toast("לא הצלחתי לפתוח את " + name);
        }
    }

    private void openHdmi() {
        android.media.tv.TvInputManager tim =
                (android.media.tv.TvInputManager) getSystemService(TV_INPUT_SERVICE);
        List<android.media.tv.TvInputInfo> ports = new ArrayList<>();
        if (tim != null) {
            for (android.media.tv.TvInputInfo info : tim.getTvInputList()) {
                // HDMI ports only (a console behind a port, over HDMI-CEC, has a parent)
                if (info.getType() == android.media.tv.TvInputInfo.TYPE_HDMI && info.getParentId() == null) {
                    ports.add(info);
                }
            }
        }
        if (ports.isEmpty()) {
            toast("לא נמצאה כניסת HDMI");
            return;
        }
        android.media.tv.TvInputInfo port = ports.get(Math.min(HDMI_INDEX, ports.size() - 1));
        startApp(new Intent(Intent.ACTION_VIEW,
                android.media.tv.TvContract.buildChannelUriForPassthroughInput(port.getId())), "HDMI");
    }

    private Intent findLaunchIntent(AppTarget app) {
        PackageManager pm = getPackageManager();
        for (String pkg : app.packages) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) return i;
        }
        if (app.keywords.length == 0) return null;
        for (InstalledApp a : installedApps()) {
            String label = a.label.toLowerCase(Locale.ROOT);
            String pkg = a.intent.getComponent().getPackageName().toLowerCase(Locale.ROOT);
            for (String k : app.keywords) {
                boolean exact = k.startsWith("=");
                String key = (exact ? k.substring(1) : k).toLowerCase(Locale.ROOT);
                if (exact ? label.equals(key) : (label.contains(key) || pkg.contains(key))) return a.intent;
            }
        }
        return null;
    }

    static class InstalledApp {
        String label;
        Intent intent;
        ActivityInfo info;
    }

    /** All apps that can be opened on the TV, sorted by name. */
    private List<InstalledApp> installedApps() {
        PackageManager pm = getPackageManager();
        Map<String, InstalledApp> byPackage = new LinkedHashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || byPackage.containsKey(pkg)) continue;
                InstalledApp a = new InstalledApp();
                a.label = String.valueOf(ri.loadLabel(pm));
                a.info = ri.activityInfo;
                a.intent = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                a.intent.setClassName(pkg, ri.activityInfo.name);
                byPackage.put(pkg, a);
            }
        }
        List<InstalledApp> list = new ArrayList<>(byPackage.values());
        Collections.sort(list, (x, y) -> x.label.compareToIgnoreCase(y.label));
        return list;
    }

    // =====================================================================
    // "All apps" drawer
    // =====================================================================

    private void openDrawer() {
        if (drawer != null) return;
        PackageManager pm = getPackageManager();

        drawer = new FrameLayout(this);
        drawer.setBackgroundColor(0xF2E7DAFF);
        drawer.setClickable(true);

        TextView title = text("כל האפליקציות", varela, 36, 0, INK);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        tlp.rightMargin = 72;
        tlp.topMargin = 40;
        drawer.addView(title, tlp);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 20, 0, 40);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(rows);

        final int perRow = 5, itemW = 200, itemH = 150, gap = 28;
        LinearLayout row = null;
        View first = null;
        List<InstalledApp> list = installedApps();
        for (int i = 0; i < list.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, itemH);
                rlp.bottomMargin = gap;
                rows.addView(row, rlp);
            }
            InstalledApp a = list.get(i);
            View item = drawerItem(a, pm, itemW, itemH);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(itemW, itemH);
            ilp.setMarginEnd(gap);
            row.addView(item, ilp);
            if (first == null) first = item;
        }
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                perRow * (itemW + gap), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT);
        slp.rightMargin = 72;
        slp.topMargin = 110;
        drawer.addView(scroll, slp);

        stage.addView(drawer, new FrameLayout.LayoutParams(W, H));
        drawer.setAlpha(0f);
        drawer.animate().alpha(1f).setDuration(200).start();
        for (View t : tileViews) t.setFocusable(false);
        if (first != null) first.requestFocus();
    }

    private View drawerItem(InstalledApp a, PackageManager pm, int w, int h) {
        GradientDrawable bg = rounded(0xFFFFFFFF, 24);
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(12, 12, 12, 10);
        item.setBackground(bg);
        item.setFocusable(true);
        item.setClickable(true);

        Drawable banner = a.info.loadBanner(pm);
        ImageView pic = new ImageView(this);
        pic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (banner != null) {
            pic.setImageDrawable(banner);
            item.addView(pic, new LinearLayout.LayoutParams(w - 24, 90));
        } else {
            pic.setImageDrawable(a.info.loadIcon(pm));
            item.addView(pic, new LinearLayout.LayoutParams(72, 72));
        }
        TextView label = text(a.label, varela, 18, 0, INK);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(0, 8, 0, 0);
        item.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        item.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f).setDuration(150).start();
            bg.setStroke(focused ? 5 : 0, 0xFFB79CFF);
        });
        item.setOnClickListener(v -> {
            closeDrawer();
            startApp(new Intent(a.intent), a.label);
        });
        return item;
    }

    private void closeDrawer() {
        if (drawer == null) return;
        View d = drawer;
        drawer = null;
        for (View t : tileViews) t.setFocusable(true);
        stage.removeView(d);
        tileViews.get(tileViews.size() - 1).requestFocus();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private View add(View v, int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = x;
        lp.topMargin = y;
        stage.addView(v, lp);
        return v;
    }

    private void addRight(View v, int right, int top) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        lp.rightMargin = right;
        lp.topMargin = top;
        stage.addView(v, lp);
    }

    private ImageView image(int resId) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(resId);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        return iv;
    }

    /** Scene layers are drawn at 1:1 with the 560x300 mockup, so pivots are in mockup pixels. */
    private void pivot(View v, float x, float y) {
        v.setPivotX(x);
        v.setPivotY(y);
    }

    private TextView text(String s, Typeface font, int sizePx, int weight, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        style(t, font, sizePx, weight, color);
        return t;
    }

    private void style(TextView t, Typeface font, int sizePx, int weight, int color) {
        t.setTypeface(font);
        if (weight > 0) t.setFontVariationSettings("'wght' " + weight);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        t.setTextColor(color);
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private View glowBlob(int color, float radius) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(radius);
        g.setColors(new int[]{color, color & 0x00FFFFFF});
        v.setBackground(g);
        return v;
    }

    private void drift(View v, float dx, float dy, int halfPeriod) {
        pingPong(ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, dx),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, dy),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f)), halfPeriod, 0);
    }

    private void pingPong(ObjectAnimator a, int halfPeriod, int delay) {
        a.setDuration(halfPeriod);
        a.setStartDelay(delay);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        loops.add(a);
        a.start();
    }

    /** A 0..1 animator that repeats forever; add an update listener to use it. */
    private ValueAnimator loop(int period, int delay) {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(period);
        a.setStartDelay(delay);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new LinearInterpolator());
        loops.add(a);
        a.start();
        return a;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
