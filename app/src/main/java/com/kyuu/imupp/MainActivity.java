package com.kyuu.imupp;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private EditText etUrl;
    private ImageButton btnBack, btnForward, btnGo;
    private Button btnToggleDevTools, btnCopyCookies;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;

    // Enhanced Eruda CDN injection script (captures logs, cookies, storage, JWT)
    private static final String ERUDA_INJECT_SCRIPT =
            "javascript:(function () { " +
            "if (window.eruda) { window.eruda.show(); return; } " +
            "var script = document.createElement('script'); " +
            "script.src = 'https://cdn.jsdelivr.net/npm/eruda'; " +
            "document.body.appendChild(script); " +
            "script.onload = function () { eruda.init({ defaults: { displaySize: 50, transparency: 0.9 } }); eruda.show(); }; " +
            "})();";

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        etUrl = findViewById(R.id.etUrl);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnGo = findViewById(R.id.btnGo);
        btnCopyCookies = findViewById(R.id.btnCopyCookies);
        btnToggleDevTools = findViewById(R.id.btnToggleDevTools);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        // Configure WebView Settings
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        // Enable Cookie Access (Third Party & HttpOnly)
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Enable Remote Debugging for Chrome inspect (USB)
        WebView.setWebContentsDebuggingEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                etUrl.setText(url);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                // Flush cookies so all HttpOnly/session cookies are updated instantly
                CookieManager.getInstance().flush();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }
        });

        // Navigation listeners
        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });

        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });

        btnGo.setOnClickListener(v -> loadUrlFromInput());

        etUrl.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadUrlFromInput();
                return true;
            }
            return false;
        });

        btnCopyCookies.setOnClickListener(v -> copyAllCookiesAndStorage());

        btnToggleDevTools.setOnClickListener(v -> {
            injectEruda();
            Toast.makeText(MainActivity.this, "DevTools Loaded!", Toast.LENGTH_SHORT).show();
        });

        swipeRefresh.setOnRefreshListener(() -> webView.reload());

        // Default home page
        webView.loadUrl("https://google.com");
    }

    private void loadUrlFromInput() {
        String url = etUrl.getText().toString().trim();
        if (!url.isEmpty()) {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                if (url.contains(".") && !url.contains(" ")) {
                    url = "https://" + url;
                } else {
                    url = "https://www.google.com/search?q=" + url;
                }
            }
            webView.loadUrl(url);
        }
    }

    private void injectEruda() {
        webView.evaluateJavascript(ERUDA_INJECT_SCRIPT, null);
    }

    private void copyAllCookiesAndStorage() {
        String currentUrl = webView.getUrl();
        if (currentUrl == null) currentUrl = "https://facebook.com";

        // Get full raw Cookie (including HttpOnly cookies like xs, c_user, etc.)
        String rawCookies = CookieManager.getInstance().getCookie(currentUrl);

        if (rawCookies == null || rawCookies.isEmpty()) {
            Toast.makeText(this, "Tidak ada Cookie yang ditemukan pada halaman ini!", Toast.LENGTH_LONG).show();
            return;
        }

        // Copy directly to Android Clipboard
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Browser Cookies & Tokens", rawCookies);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "📋 Berhasil Salin Semua Cookie (Termasuk c_user, xs, token)!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
