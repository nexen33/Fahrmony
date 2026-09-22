package com.fahrmony.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebView;
import com.fahrmony.app.nativebridge.FahrmonyIpcBridge;
import com.fahrmony.app.nativebridge.FahrmonyLogBuffer;
import com.fahrmony.app.nativebridge.FahrmonyMediaManager;
import com.fahrmony.app.nativebridge.FahrmonyPlugin;
import com.fahrmony.app.nativebridge.FahrmonyUpdateManager;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        Configuration config = newBase.getResources().getConfiguration();
        if (config.fontScale != 1.0f) {
            Configuration newConfig = new Configuration(config);
            newConfig.fontScale = 1.0f;
            Context context = newBase.createConfigurationContext(newConfig);
            super.attachBaseContext(context);
            return;
        }
        super.attachBaseContext(newBase);
    }

    private void handleChainLaunch(Intent intent) {
        if (intent == null) return;
        String chainPkg = intent.getStringExtra("EXTRA_CHAIN_LAUNCH_PKG");
        if (chainPkg != null && !chainPkg.trim().isEmpty()) {
            intent.removeExtra("EXTRA_CHAIN_LAUNCH_PKG");
            FahrmonyLogBuffer.INSTANCE.addLog("WAKE", "MainActivity链式处理", "收到链式启动目标: " + chainPkg, chainPkg, null);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Intent targetIntent = getPackageManager().getLaunchIntentForPackage(chainPkg);
                    if (targetIntent == null && FahrmonyMediaManager.INSTANCE.isPackageMatch(chainPkg, "com.kugou.android")) {
                        targetIntent = getPackageManager().getLaunchIntentForPackage("com.kugou.android");
                        if (targetIntent == null) {
                            targetIntent = getPackageManager().getLaunchIntentForPackage("com.kugou.android.lite");
                        }
                    }
                    if (targetIntent != null) {
                        targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                        startActivity(targetIntent);
                        FahrmonyLogBuffer.INSTANCE.addLog("WAKE", "MainActivity链式拉起目标", "已调用startActivity拉起: " + chainPkg, chainPkg, null);
                    }
                } catch (Exception e) {
                    FahrmonyLogBuffer.INSTANCE.addLog("WAKE", "MainActivity链式拉起异常", "" + e.getMessage(), chainPkg, null);
                }
            }, 300L);
        }
    }

    private void applyPersistedTheme() {
        SharedPreferences sp = getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
        String savedTheme = sp.getString("fahrmony_theme", "system");
        boolean isDark;
        if ("system".equals(savedTheme)) {
            int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isDark = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);
        } else {
            isDark = "dark".equals(savedTheme);
        }
        int bgColor = Color.parseColor(isDark ? "#0A0C10" : "#F8FAFC");

        // 同步窗口底色与 WebView 底色，杜绝使用 setDefaultNightMode 触发 Activity recreate() 与白屏
        getWindow().setBackgroundDrawable(new ColorDrawable(bgColor));

        if (getBridge() != null && getBridge().getWebView() != null) {
            WebView webView = getBridge().getWebView();
            webView.setBackgroundColor(bgColor);
            webView.getSettings().setTextZoom(83);
            webView.setVerticalScrollBarEnabled(false);
            webView.setHorizontalScrollBarEnabled(false);
            webView.invalidate();
        }
    }

    private void checkFirstLaunchPostNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            SharedPreferences sp = getSharedPreferences("fahrmony_settings", Context.MODE_PRIVATE);
            boolean hasPrompted = sp.getBoolean("has_prompted_post_notifications", false);
            if (!hasPrompted) {
                sp.edit().putBoolean("has_prompted_post_notifications", true).apply();
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1002);
                }
            }
        }
    }

    private void enableLockscreenFlags() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableLockscreenFlags();
        applyPersistedTheme();
        registerPlugin(FahrmonyPlugin.class);
        super.onCreate(savedInstanceState);

        // 视图树加载完毕后再次确保 WebView 底色与持久化主题保持像素级一致
        applyPersistedTheme();
        FahrmonyMediaManager.INSTANCE.init(this);
        FahrmonyUpdateManager.INSTANCE.init(this);
        handleChainLaunch(getIntent());
        checkFirstLaunchPostNotifications();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        enableLockscreenFlags();
        setIntent(intent);
        handleChainLaunch(intent);
    }

    @Override
    public void onStart() {
        super.onStart();
        applyPersistedTheme();
    }

    @Override
    public void onResume() {
        super.onResume();
        applyPersistedTheme();
        FahrmonyIpcBridge.INSTANCE.requestRefresh();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 系统切入车载模式 (UI_MODE_TYPE_CAR) 或多屏投射时，执行无感防抖与主题对齐，防止 WebView 渲染管线挂起导致灰白屏
        applyPersistedTheme();
        FahrmonyIpcBridge.INSTANCE.requestRefresh();
    }
}
