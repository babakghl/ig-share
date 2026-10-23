package com.hana.igshare;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

/**
 * One-time Instagram login on Instagram's own page. As soon as the
 * session cookie appears, it is saved (on this device only) and the
 * activity closes.
 */
public class LoginActivity extends Activity {

    private WebView web;
    private boolean done = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        TextView hint = findViewById(R.id.loginHint);
        hint.setText("Log in on Instagram's page below. Your password goes "
                + "only to Instagram — this app keeps just the session "
                + "cookie, stored on your phone, so downloads can work.");

        web = findViewById(R.id.web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 14; SM-S928W) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/126.0.0.0 Mobile Safari/537.36");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                checkSession();
            }
        });
        findViewById(R.id.btnLoginDone).setOnClickListener(v -> checkSession());
        web.loadUrl("https://www.instagram.com/accounts/login/");
    }

    private void checkSession() {
        if (done) return;
        String cookies = CookieManager.getInstance()
                .getCookie("https://www.instagram.com");
        if (cookies == null) return;
        String sessionid = null, csrf = null, user = null;
        for (String part : cookies.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("sessionid")) sessionid = kv[1];
            if (kv[0].equals("csrftoken")) csrf = kv[1];
            if (kv[0].equals("ds_user_id")) user = kv[1];
        }
        if (sessionid != null && !sessionid.isEmpty()) {
            done = true;
            IgSession.save(this, sessionid, csrf, user);
            Toast.makeText(this, "Connected to Instagram",
                    Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }
}
