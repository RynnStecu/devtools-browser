package com.kyuu.imupp;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private EditText etUrl;
    private ImageButton btnBack, btnForward, btnReload, btnMenu;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;

    private boolean isDesktopMode = false;
    private String customUserAgent = null;

    private static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final String DEFAULT_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36";

    private static final String ERUDA_INJECT_SCRIPT =
            "javascript:(function () { " +
            "if (window.eruda) { window.eruda.show(); return; } " +
            "var script = document.createElement('script'); " +
            "script.src = 'https://cdn.jsdelivr.net/npm/eruda'; " +
            "document.body.appendChild(script); " +
            "script.onload = function () { eruda.init({ defaults: { displaySize: 50, transparency: 0.9 } }); eruda.show(); }; " +
            "})();";

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        etUrl = findViewById(R.id.etUrl);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnReload = findViewById(R.id.btnReload);
        btnMenu = findViewById(R.id.btnMenu);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        // WebSettings Optimization
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(DEFAULT_UA);

        // Cookies setup
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Remote Debugging
        WebView.setWebContentsDebuggingEnabled(true);

        // WebView Client Setup
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (!etUrl.hasFocus()) {
                    etUrl.setText(url);
                }
                progressBar.setVisibility(View.VISIBLE);
                btnReload.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                btnReload.setImageResource(android.R.drawable.ic_popup_sync);
                CookieManager.getInstance().flush();
                injectScraperBridge();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlLoading(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrlLoading(view, request.getUrl().toString());
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }
        });

        // Navigation Handlers
        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });

        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });

        btnReload.setOnClickListener(v -> {
            if (progressBar.getVisibility() == View.VISIBLE) {
                webView.stopLoading();
            } else {
                webView.reload();
            }
        });

        btnMenu.setOnClickListener(v -> showFeatureMenu());

        etUrl.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadUrlFromInput();
                return true;
            }
            return false;
        });

        swipeRefresh.setOnRefreshListener(() -> webView.reload());

        // Default Load
        webView.loadUrl("https://google.com");
    }

    private boolean handleUrlLoading(WebView view, String url) {
        if (url == null) return false;

        // Standard HTTP / HTTPS
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false;
        }

        // Handle intent:// scheme links (Google Maps, Telegram, TikTok, Intent Deep Links)
        if (url.startsWith("intent://")) {
            try {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                if (intent != null) {
                    if (getPackageManager().resolveActivity(intent, 0) != null) {
                        startActivity(intent);
                        return true;
                    }

                    // Fallback 1: S.browser_fallback_url
                    String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                    if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                        view.loadUrl(fallbackUrl);
                        return true;
                    }

                    // Fallback 2: Google Play Store
                    String packageName = intent.getPackage();
                    if (packageName != null) {
                        showSchemeFallbackDialog("App Not Installed", "App with package '" + packageName + "' is not installed.", "https://play.google.com/store/apps/details?id=" + packageName, url);
                        return true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            showSchemeFallbackDialog("Open External Link", "Could not process intent automatically.", null, url);
            return true;
        }

        // Handle standard custom schemes: tel:, mailto:, sms:, geo:, whatsapp:, tg:, android-app://, etc.
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (getPackageManager().resolveActivity(intent, 0) != null) {
                startActivity(intent);
                return true;
            } else {
                showSchemeFallbackDialog("App Required", "No compatible app found for: " + url, null, url);
                return true;
            }
        } catch (Exception e) {
            showSchemeFallbackDialog("Scheme Error", "Failed to launch scheme link.", null, url);
            return true;
        }
    }

    private void showSchemeFallbackDialog(String title, String message, String fallbackUrl, String rawLink) {
        String[] options = fallbackUrl != null 
                ? new String[]{"🌐 Open Fallback URL", "📋 Copy Raw Link", "🔗 Share Link"} 
                : new String[]{"📋 Copy Raw Link", "🔗 Share Link"};

        new AlertDialog.Builder(this)
                .setTitle("🚀 " + title)
                .setMessage(message)
                .setItems(options, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (fallbackUrl != null) {
                        if (which == 0) webView.loadUrl(fallbackUrl);
                        else if (which == 1 && clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Link", rawLink));
                            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
                        } else if (which == 2) shareText(rawLink);
                    } else {
                        if (which == 0 && clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Link", rawLink));
                            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
                        } else if (which == 1) shareText(rawLink);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareText(String text) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share via"));
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

    private void injectScraperBridge() {
        try {
            InputStream is = getAssets().open("scraper_bridge.js");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String jsCode = new String(buffer, StandardCharsets.UTF_8);
            webView.evaluateJavascript(jsCode, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showFeatureMenu() {
        String[] options = {
                "🛠️ Launch Native DevTools",
                "📊 Scrape Tools (HTML, Links, Storage)",
                "🍪 Copy All Cookies & Tokens",
                "🖥️ " + (isDesktopMode ? "Switch to Mobile View" : "Switch to Desktop Site"),
                "🤖 Change User-Agent",
                "🔍 Find in Page",
                "🏠 Go to Home"
        };

        new AlertDialog.Builder(this)
                .setTitle("⚙️ Browser & Scraper Tools")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            injectNativeDevTools();
                            break;
                        case 1:
                            showScraperSubMenu();
                            break;
                        case 2:
                            copyAllCookiesAndStorage();
                            break;
                        case 3:
                            toggleDesktopMode();
                            break;
                        case 4:
                            showUserAgentDialog();
                            break;
                        case 5:
                            showFindInPageDialog();
                            break;
                        case 6:
                            webView.loadUrl("https://google.com");
                            break;
                    }
                })
                .show();
    }

    private void showScraperSubMenu() {
        String[] scraperOptions = {
                "📋 Extract Page Metadata (Title, Meta tags)",
                "🔗 Extract All Links (JSON)",
                "🖼️ Extract All Media (Images/Videos)",
                "📊 Extract All HTML Tables (JSON)",
                "🔑 Extract Cookies + LocalStorage + SessionStorage",
                "📄 Extract Clean Page Text",
                "🌐 Copy Full Raw HTML Source",
                "🎯 Query Selector Scraper (CSS Selector)"
        };

        new AlertDialog.Builder(this)
                .setTitle("🕷️ Select Data Scraper Tool")
                .setItems(scraperOptions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            runScraperJS("ScraperBridge.getMetaData()", "Page Metadata");
                            break;
                        case 1:
                            runScraperJS("ScraperBridge.getAllLinks()", "Extracted Links");
                            break;
                        case 2:
                            runScraperJS("ScraperBridge.getAllMedia()", "Extracted Media");
                            break;
                        case 3:
                            runScraperJS("ScraperBridge.getTables()", "Extracted Tables");
                            break;
                        case 4:
                            runScraperJS("ScraperBridge.getStorageAndCookies()", "Storage & Cookies");
                            break;
                        case 5:
                            runScraperJS("ScraperBridge.getText()", "Clean Text Content");
                            break;
                        case 6:
                            runScraperJS("ScraperBridge.getHTML()", "HTML Source Code");
                            break;
                        case 7:
                            showCustomSelectorDialog();
                            break;
                    }
                })
                .show();
    }

    private void runScraperJS(String jsExpression, String title) {
        injectScraperBridge();
        webView.evaluateJavascript(jsExpression, value -> {
            if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
                value = unescapeJSString(value);
            }
            showResultDialog(title, value);
        });
    }

    private String unescapeJSString(String s) {
        if (s == null) return "";
        try {
            return org.json.JSONObject.quote(s).substring(1, s.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\r", "")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\");
        } catch (Exception e) {
            return s;
        }
    }

    private void showCustomSelectorDialog() {
        final EditText input = new EditText(this);
        input.setHint("e.g. div.product-title or a.btn");
        input.setPadding(30, 20, 30, 20);

        new AlertDialog.Builder(this)
                .setTitle("🎯 Query Selector Scraper")
                .setMessage("Masukkan CSS Selector element yang ingin ditarik:")
                .setView(input)
                .setPositiveButton("Scrape Data", (dialog, which) -> {
                    String selector = input.getText().toString().trim();
                    if (!selector.isEmpty()) {
                        String js = "ScraperBridge.querySelectorAllData('" + selector.replace("'", "\\'") + "')";
                        runScraperJS(js, "Query Result: " + selector);
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showResultDialog(String title, String data) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        final EditText text = new EditText(this);
        text.setText(data);
        text.setFocusable(true);
        text.setSelectAllOnFocus(true);
        text.setTextIsSelectable(true);
        text.setMaxLines(20);
        builder.setView(text);

        builder.setPositiveButton("📋 Copy Result", (dialog, which) -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(title, data);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, "📋 Hasil berhasil disalin!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Tutup", null);
        builder.show();
    }

    private void injectNativeDevTools() {
        try {
            InputStream is = getAssets().open("native_devtools/devtools_core.js");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String jsCode = new String(buffer, StandardCharsets.UTF_8);
            webView.evaluateJavascript(jsCode, null);
            Toast.makeText(this, "⚡ Native DevTools Activated!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to inject Native DevTools", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyAllCookiesAndStorage() {
        String currentUrl = webView.getUrl();
        if (currentUrl == null) currentUrl = "https://facebook.com";

        String rawCookies = CookieManager.getInstance().getCookie(currentUrl);

        if (rawCookies == null || rawCookies.isEmpty()) {
            Toast.makeText(this, "Tidak ada Cookie yang ditemukan pada halaman ini!", Toast.LENGTH_LONG).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Browser Cookies", rawCookies);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "📋 Berhasil Salin Semua Cookie!", Toast.LENGTH_LONG).show();
        }
    }

    private void toggleDesktopMode() {
        isDesktopMode = !isDesktopMode;
        WebSettings settings = webView.getSettings();
        if (isDesktopMode) {
            settings.setUserAgentString(DESKTOP_UA);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            Toast.makeText(this, "🖥️ Desktop Mode Activated", Toast.LENGTH_SHORT).show();
        } else {
            settings.setUserAgentString(customUserAgent != null ? customUserAgent : DEFAULT_UA);
            Toast.makeText(this, "📱 Mobile View Activated", Toast.LENGTH_SHORT).show();
        }
        webView.reload();
    }

    private void showUserAgentDialog() {
        String[] uas = {
                "Android Chrome Mobile (Default)",
                "Windows Chrome Desktop",
                "iPhone Mobile Safari",
                "Googlebot / Web Crawler",
                "Custom User-Agent String"
        };

        new AlertDialog.Builder(this)
                .setTitle("🤖 Select User-Agent")
                .setItems(uas, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            customUserAgent = DEFAULT_UA;
                            webView.getSettings().setUserAgentString(DEFAULT_UA);
                            webView.reload();
                            break;
                        case 1:
                            customUserAgent = DESKTOP_UA;
                            webView.getSettings().setUserAgentString(DESKTOP_UA);
                            webView.reload();
                            break;
                        case 2:
                            customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1";
                            webView.getSettings().setUserAgentString(customUserAgent);
                            webView.reload();
                            break;
                        case 3:
                            customUserAgent = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
                            webView.getSettings().setUserAgentString(customUserAgent);
                            webView.reload();
                            break;
                        case 4:
                            promptCustomUserAgent();
                            break;
                    }
                })
                .show();
    }

    private void promptCustomUserAgent() {
        final EditText input = new EditText(this);
        input.setText(webView.getSettings().getUserAgentString());

        new AlertDialog.Builder(this)
                .setTitle("Custom User-Agent")
                .setView(input)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    String ua = input.getText().toString().trim();
                    if (!ua.isEmpty()) {
                        customUserAgent = ua;
                        webView.getSettings().setUserAgentString(ua);
                        webView.reload();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showFindInPageDialog() {
        final EditText input = new EditText(this);
        input.setHint("Search text...");
        new AlertDialog.Builder(this)
                .setTitle("🔍 Find in Page")
                .setView(input)
                .setPositiveButton("Cari", (dialog, which) -> {
                    String query = input.getText().toString();
                    if (!query.isEmpty()) {
                        webView.findAllAsync(query);
                    }
                })
                .setNeutralButton("Clear", (dialog, which) -> webView.clearMatches())
                .setNegativeButton("Batal", null)
                .show();
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

