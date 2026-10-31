package com.blackbox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFAAAAAA;
    private static final int DARK = 0xFF222222;
    private static final int AMBER = 0xFFFFB74D;

    private static class AppInfo {
        final String pkg;
        final String label;
        final Drawable icon;

        AppInfo(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, TextView> exceptionButtons = new HashMap<>();
    private LinearLayout content;
    private TextView timerView;
    private EditText minutesInput;
    private String lastMinutes = "60";
    private List<AppInfo> apps = null;
    private boolean loading = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (Store.isLocked(MainActivity.this)) {
                if (timerView != null) {
                    timerView.setText(Store.format(Store.remaining(MainActivity.this)));
                }
                handler.postDelayed(this, 1000L);
            } else {
                render();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(32), dp(20), dp(32));
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        if (apps == null && !loading) loadApps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    // ---- app list -----------------------------------------------------------------

    private void loadApps() {
        loading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> result = queryApps();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        apps = result;
                        loading = false;
                        if (!isFinishing()) render();
                    }
                });
            }
        }).start();
    }

    private List<AppInfo> queryApps() {
        PackageManager pm = getPackageManager();

        // Never offer the phone/dialer, home screen, system UI or Black Box itself,
        // so you can always make and receive normal calls.
        Set<String> skip = new HashSet<>();
        skip.add(getPackageName());
        skip.add("android");
        skip.add("com.android.systemui");
        skip.add("com.android.phone");
        skip.add("com.android.server.telecom");
        skip.add("com.android.dialer");
        skip.add("com.google.android.dialer");
        skip.add("com.android.emergency");
        try {
            TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (tm != null) {
                String dialer = tm.getDefaultDialerPackage();
                if (dialer != null) skip.add(dialer);
            }
        } catch (Exception ignored) {
        }
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : pm.queryIntentActivities(home, 0)) {
            skip.add(ri.activityInfo.packageName);
        }

        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<AppInfo> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String p = ri.activityInfo.packageName;
            if (skip.contains(p) || !seen.add(p)) continue;
            out.add(new AppInfo(p, String.valueOf(ri.loadLabel(pm)), ri.loadIcon(pm)));
        }
        Collections.sort(out, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    private String labelFor(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    // ---- screens ------------------------------------------------------------------

    private void render() {
        handler.removeCallbacks(tick);
        if (minutesInput != null) lastMinutes = minutesInput.getText().toString();
        content.removeAllViews();
        exceptionButtons.clear();
        timerView = null;
        minutesInput = null;

        content.addView(text("Black Box", 30, WHITE, true));
        gap(4);
        if (Store.isLocked(this)) {
            renderLocked();
        } else {
            renderSetup();
        }
    }

    private void renderLocked() {
        content.addView(text("LOCKED", 14, AMBER, true));
        gap(16);

        timerView = text(Store.format(Store.remaining(this)), 52, WHITE, true);
        timerView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        content.addView(timerView);
        gap(4);
        content.addView(text("time left", 14, GREY, false));
        gap(24);

        if (!accessibilityEnabled()) {
            content.addView(text("Accessibility is OFF, so nothing is being blocked right now. Switch Black Box back on.", 15, AMBER, false));
            gap(8);
            content.addView(button("Open Accessibility settings", WHITE, Color.BLACK, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                }
            }));
            gap(24);
        }

        content.addView(text("In the box", 16, WHITE, true));
        gap(6);
        String exc = Store.getException(this);
        for (String pkg : Store.getBlocked(this)) {
            if (pkg.equals(exc)) continue;
            content.addView(text("•  " + labelFor(pkg), 16, GREY, false));
        }

        if (exc != null) {
            gap(20);
            content.addView(text("Exception (still allowed)", 16, WHITE, true));
            gap(6);
            content.addView(text("•  " + labelFor(exc), 16, GREY, false));
            gap(8);
            content.addView(button("Remove exception (block it too)", DARK, WHITE, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Store.clearException(MainActivity.this);
                    render();
                }
            }));
        }

        gap(28);
        content.addView(text("There is no unlock button, on purpose. If you really must get out, for example for an urgent call on a blocked app, restart your phone and the lock is cleared.", 14, GREY, false));

        handler.postDelayed(tick, 1000L);
    }

    private void renderSetup() {
        content.addView(text("Put your dopamine apps in the box.", 15, GREY, false));
        gap(20);

        if (!accessibilityEnabled()) {
            content.addView(text("Step 1: turn on Black Box", 16, AMBER, true));
            gap(4);
            content.addView(text("Open Accessibility settings, find Black Box (it may be under Installed or Downloaded apps) and switch it on. It only checks which app is open.", 14, GREY, false));
            gap(8);
            content.addView(button("Open Accessibility settings", WHITE, Color.BLACK, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                }
            }));
            gap(24);
        }

        content.addView(text("Apps to block", 16, WHITE, true));
        gap(2);
        content.addView(text("Tick the apps. Tap the star on ONE app if you want it to stay allowed (the exception).", 13, GREY, false));
        gap(8);
        if (apps == null) {
            content.addView(text("Loading apps...", 14, GREY, false));
        } else {
            Set<String> blocked = Store.getBlocked(this);
            String exc = Store.getException(this);
            for (AppInfo a : apps) {
                content.addView(appRow(a, blocked.contains(a.pkg), a.pkg.equals(exc)));
            }
        }

        gap(24);
        content.addView(text("For how long?", 16, WHITE, true));
        gap(8);
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        final int[] mins = {30, 60, 120, 240};
        String[] names = {"30 min", "1 hr", "2 hr", "4 hr"};
        minutesInput = new EditText(this);
        minutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutesInput.setText(lastMinutes);
        minutesInput.setTextColor(WHITE);
        minutesInput.setHintTextColor(GREY);
        minutesInput.setHint("minutes");
        for (int i = 0; i < mins.length; i++) {
            final int m = mins[i];
            Button b = button(names[i], DARK, WHITE, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    minutesInput.setText(String.valueOf(m));
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            presets.addView(b, lp);
        }
        content.addView(presets);
        gap(8);
        content.addView(text("or type minutes (1 to 1440):", 13, GREY, false));
        content.addView(minutesInput);

        gap(16);
        CheckBox protect = new CheckBox(this);
        protect.setText("Also block Settings and uninstall while locked, so I can't switch Black Box off");
        protect.setTextColor(GREY);
        protect.setChecked(Store.getProtect(this));
        protect.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                Store.setProtect(MainActivity.this, on);
            }
        });
        content.addView(protect);

        gap(24);
        content.addView(button("Start lock", WHITE, Color.BLACK, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStart();
            }
        }));
    }

    private View appRow(final AppInfo a, boolean checked, boolean isException) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(a.icon);
        row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

        final CheckBox cb = new CheckBox(this);
        cb.setText(a.label);
        cb.setTextColor(WHITE);
        cb.setChecked(checked);
        cb.setPadding(dp(8), 0, 0, 0);
        row.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView star = new TextView(this);
        star.setTextSize(14);
        star.setPadding(dp(10), dp(8), dp(10), dp(8));
        star.setVisibility(checked ? View.VISIBLE : View.GONE);
        setStarState(star, isException);
        exceptionButtons.put(a.pkg, star);
        star.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (a.pkg.equals(Store.getException(MainActivity.this))) {
                    Store.clearException(MainActivity.this);
                } else {
                    Store.setException(MainActivity.this, a.pkg);
                }
                refreshStars();
            }
        });
        row.addView(star);

        cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                Set<String> s = Store.getBlocked(MainActivity.this);
                if (on) {
                    s.add(a.pkg);
                } else {
                    s.remove(a.pkg);
                    if (a.pkg.equals(Store.getException(MainActivity.this))) {
                        Store.clearException(MainActivity.this);
                    }
                }
                Store.setBlocked(MainActivity.this, s);
                star.setVisibility(on ? View.VISIBLE : View.GONE);
                refreshStars();
            }
        });
        return row;
    }

    private void setStarState(TextView star, boolean isException) {
        star.setText(isException ? "★ exception" : "☆ exception");
        star.setTextColor(isException ? 0xFFFFD54F : 0xFF777777);
    }

    private void refreshStars() {
        String exc = Store.getException(this);
        for (Map.Entry<String, TextView> e : exceptionButtons.entrySet()) {
            setStarState(e.getValue(), e.getKey().equals(exc));
        }
    }

    // ---- starting a lock ----------------------------------------------------------

    private void onStart() {
        if (!accessibilityEnabled()) {
            toast("First turn on Black Box in Accessibility settings.");
            return;
        }
        Set<String> effective = Store.getBlocked(this);
        String exc = Store.getException(this);
        if (exc != null) effective.remove(exc);
        if (effective.isEmpty()) {
            toast("Pick at least one app to block.");
            return;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(minutesInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            minutes = 0;
        }
        if (minutes < 1 || minutes > 1440) {
            toast("Enter between 1 and 1440 minutes.");
            return;
        }
        final int fm = minutes;
        new AlertDialog.Builder(this)
                .setTitle("Lock for " + describe(fm) + "?")
                .setMessage(effective.size() + " app(s) will be fully blocked. There is no unlock button. The only way out early is restarting your phone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Lock now", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Store.startLock(MainActivity.this, fm * 60000L);
                        render();
                    }
                })
                .show();
    }

    private String describe(int minutes) {
        if (minutes % 60 == 0) return (minutes / 60) + " hr";
        if (minutes > 60) return (minutes / 60) + " hr " + (minutes % 60) + " min";
        return minutes + " min";
    }

    // ---- helpers ------------------------------------------------------------------

    private boolean accessibilityEnabled() {
        try {
            int on = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (on != 1) return false;
            String list = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (list == null) return false;
            ComponentName cn = new ComponentName(this, BlockerService.class);
            String full = cn.flattenToString();
            String shortName = cn.flattenToShortString();
            for (String part : list.split(":")) {
                if (part.equalsIgnoreCase(full) || part.equalsIgnoreCase(shortName)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void gap(int heightDp) {
        content.addView(new View(this), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String label, int bg, int fg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(16);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(12));
        b.setBackground(g);
        b.setOnClickListener(l);
        return b;
    }
}
