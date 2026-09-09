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
import com.fahrmony.app.nativebridge.FahrmonyMediaManager;
import com.fahrmony.app.nativebridge.FahrmonyPlugin;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private void handleChainLaunch(Intent intent) {
        if (intent == null) return;
        String chainPkg = intent.getStringExtra("EXTRA_CHAIN_LAUNCH_PKG");
        if (chainPkg != null && !chainPkg.trim().isEmpty()) {
            intent.removeExtra("EXTRA_CHAIN_LAUNCH_PKG");
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Intent targetIntent = getPackageManager().getLaunchIntentForPackage(chainPkg);
                    if (targetIntent != null) {
                        targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                        startActivity(targetIntent);
                    }
                } catch (Exception ignored) {}
            }, 300L);
        }
    }

    private void applyPersistedTheme() {
        SharedPreferences sp = getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
        String savedTheme = sp.getString("fahrmony_theme", "light");
        boolean isDark = "dark".equals(savedTheme);
        int bgColor = Color.parseColor(isDark ? "#0B0D12" : "#F8FAFC");

        // 同步窗口底色与 WebView 底色，杜绝使用 setDefaultNightMode 触发 Activity recreate() 与白屏
        getWindow().setBackgroundDrawable(new ColorDrawable(bgColor));

        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().setBackgroundColor(bgColor);
            getBridge().getWebView().invalidate();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        applyPersistedTheme();
        registerPlugin(FahrmonyPlugin.class);
        super.onCreate(savedInstanceState);

        // 视图树加载完毕后再次确保 WebView 底色与持久化主题保持像素级一致
        applyPersistedTheme();
        FahrmonyMediaManager.INSTANCE.init(this);
        handleChainLaunch(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleChainLaunch(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        applyPersistedTheme();
        FahrmonyMediaManager.INSTANCE.refresh(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 系统切入车载模式 (UI_MODE_TYPE_CAR) 或多屏投射时，执行无感防抖与主题对齐，防止 WebView 渲染管线挂起导致灰白屏
        applyPersistedTheme();
        FahrmonyMediaManager.INSTANCE.refresh(this);
    }
}
