package br.com.centraleleicoes.webview;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.InputStream;

/** Casca WebView: carrega o HTML final empacotado em assets/index.html. Sem ActionBar, edge-to-edge. */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + HOST + "/index.html";
    private static final int BG = Color.parseColor("#040913");

    private FrameLayout root;
    private WebView webView;
    private NativeBridge bridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        setupEdgeToEdge();
        bridge = new NativeBridge(id -> {
            final WebView w = webView;
            if (w != null) w.post(() -> {
                try { w.evaluateJavascript("window.__nativeHttpDone&&window.__nativeHttpDone('" + id + "')", null); }
                catch (Throwable ignored) { }
            });
        });
        createWebView();
        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(HOME);
    }

    private void setupEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            });
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) c.setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        }
    }

    private void createWebView() {
        WebView w = new WebView(this);
        w.setBackgroundColor(BG);
        w.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        w.addJavascriptInterface(bridge, "CentralNative");
        w.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) {
                    String p = u.getPath();
                    if (p == null || p.equals("/") || p.equals("/index.html")) {
                        try {
                            InputStream in = getAssets().open("index.html");
                            WebResourceResponse r = new WebResourceResponse("text/html", "utf-8", in);
                            r.setStatusCodeAndReasonPhrase(200, "OK");
                            return r;
                        } catch (Throwable t) { /* cai para 404 abaixo */ }
                    }
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                String sch = u.getScheme();
                if ("http".equals(sch) || "https".equals(sch)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Throwable ignored) { }
                }
                return true; // nunca navega para fora da página do app
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, android.webkit.WebResourceError err) {
                // falhas de recurso/rede são tratadas pela própria página; nada de fechar o app
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) { }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // O processo do renderizador morreu (memória etc.): recria a WebView em vez de derrubar o app.
                if (view == webView) {
                    root.removeView(view);
                    view.destroy();
                    webView = null;
                    createWebView();
                    webView.loadUrl(HOME);
                }
                return true;
            }
        });
        if (webView != null) root.removeView(webView);
        webView = w;
        root.addView(w, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        final WebView w = webView;
        if (w == null) { super.onBackPressed(); return; }
        w.evaluateJavascript("(function(){var s=document.getElementById('sheet');if(s&&s.classList.contains('show')){s.classList.remove('show');return 1}"
                + "if(document.body.classList.contains('tv-mode')){document.body.classList.remove('tv-mode');return 1}return 0})()",
                value -> { if (!"1".equals(value)) finish(); });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (webView != null) webView.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (bridge != null) bridge.shutdown();
        if (webView != null) { root.removeView(webView); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
