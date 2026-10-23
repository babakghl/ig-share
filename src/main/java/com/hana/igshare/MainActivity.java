package com.hana.igshare;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {

    private android.widget.TextView tvIgStatus;
    private android.widget.Button btnIgConnect;

    @Override
    protected void onResume() {
        super.onResume();
        refreshIgStatus();
    }

    private void refreshIgStatus() {
        if (tvIgStatus == null) return;
        boolean connected = IgSession.hasSession(this);
        tvIgStatus.setText(connected ? "Instagram: connected ✓"
                : "Instagram: not connected");
        btnIgConnect.setText(connected ? "Disconnect Instagram"
                : "Connect Instagram");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cleanOldShares();

        tvIgStatus = findViewById(R.id.tvIgStatus);
        btnIgConnect = findViewById(R.id.btnIgConnect);
        btnIgConnect.setOnClickListener(v -> {
            if (IgSession.hasSession(this)) {
                IgSession.clear(this);
                android.webkit.CookieManager.getInstance().removeAllCookies(null);
                Toast.makeText(this, "Disconnected from Instagram",
                        Toast.LENGTH_SHORT).show();
                refreshIgStatus();
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
        });
        refreshIgStatus();

        EditText et = findViewById(R.id.etLink);
        // Prefill from the clipboard when it holds an Instagram link
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence t = clip.getItemAt(0).getText();
                    if (t != null && IgFetcher.extractShortcode(t.toString()) != null) {
                        et.setText(t.toString());
                    }
                }
            }
        } catch (Exception ignored) { }

        Button go = findViewById(R.id.btnGo);
        go.setOnClickListener(v -> {
            String text = et.getText().toString();
            if (IgFetcher.extractShortcode(text) == null) {
                Toast.makeText(this, "That doesn't look like an Instagram post link",
                        Toast.LENGTH_LONG).show();
                return;
            }
            Intent i = new Intent(this, ShareActivity.class);
            i.putExtra("shared_text", text);
            startActivity(i);
        });
    }

    /** Downloaded shares are transient — clear anything older than a day. */
    private void cleanOldShares() {
        try {
            File dir = ShareProvider.sharesDir(this);
            long cutoff = System.currentTimeMillis() - 24L * 3600 * 1000;
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.lastModified() < cutoff) f.delete();
                }
            }
        } catch (Exception ignored) { }
    }
}
