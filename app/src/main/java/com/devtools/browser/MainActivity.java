package com.devtools.browser;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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
    private Button btnToggleDevTools;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;

    // Eruda CDN injection script
    private static final String ERUDA_INJECT_SCRIPT =
            "javascript:(function () { " +
            "if (window.eruda) { window.eruda.show(); return; } " +
            "var script = document.createElement('script'); " +
            "script.src = 'https://cdn.jsdelivr.net/npm/eruda'; " +
            "document.body.appendChild(script); " +
            "script.onload = function () { eruda.init(); eruda.show(); }; " +
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
        btnToggleDevTools = findViewById(R.id.btnToggleDevTools);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        // Configure WebView Settings - Clean & Standard configuration
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

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

        btnToggleDevTools.setOnClickListener(v -> {
            injectEruda();
            Toast.makeText(MainActivity.this, "DevTools Activated!", Toast.LENGTH_SHORT).show();
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

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
