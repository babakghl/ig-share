package com.hana.igshare;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The share-sheet target: receives an Instagram link shared from any app,
 * downloads the media in the background, then opens a share sheet with
 * the actual files so they can go to WhatsApp, Telegram, etc.
 */
public class ShareActivity extends Activity {

    private TextView status;
    private ProgressBar progress;
    private Button btnCancel;
    private volatile boolean cancelled = false;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);
        status = findViewById(R.id.status);
        progress = findViewById(R.id.progress);
        btnCancel = findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> {
            if ("Close".contentEquals(btnCancel.getText())) {
                finish();
            } else {
                cancelled = true;
                finish();
            }
        });

        String text = getIntent().getStringExtra("shared_text");
        if (text == null) {
            CharSequence cs = getIntent().getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (cs != null) text = cs.toString();
        }
        String shortcode = IgFetcher.extractShortcode(text);
        if (shortcode == null) {
            showError("No Instagram link found in what you shared. "
                    + "Use the ⋯ menu on a post → Share, or copy the link and paste it in IG Share.");
            return;
        }
        run(shortcode);
    }

    @Override
    protected void onDestroy() {
        exec.shutdownNow();
        super.onDestroy();
    }

    private void run(String shortcode) {
        exec.execute(() -> {
            try {
                setStatus("Contacting Instagram…");
                IgFetcher.Result result = IgFetcher.fetch(this, shortcode);
                if (cancelled) return;
                if (result.items.isEmpty()) {
                    throw new IgFetcher.FetchException("No media found in this post.");
                }

                File dir = ShareProvider.sharesDir(this);
                ArrayList<Uri> uris = new ArrayList<>();
                List<String> mimes = new ArrayList<>();
                for (int i = 0; i < result.items.size(); i++) {
                    if (cancelled) return;
                    IgFetcher.Item item = result.items.get(i);
                    setStatus("Downloading " + (i + 1) + " of "
                            + result.items.size() + "…");
                    File dest = new File(dir,
                            "ig_" + shortcode + "_" + (i + 1) + "." + item.ext());
                    long bytes = IgFetcher.download(item.url, dest);
                    if (bytes == 0) throw new IgFetcher.FetchException(
                            "A download came back empty — try again.");
                    uris.add(ShareProvider.uriFor(dest));
                    mimes.add(item.video ? "video/mp4" : "image/jpeg");
                }
                if (cancelled) return;
                shareFiles(result, uris, mimes);
            } catch (Exception e) {
                if (!cancelled) {
                    String msg = e.getMessage();
                    if (!IgSession.hasSession(this)) {
                        msg += "\n\nTip: Instagram now blocks most downloads "
                                + "without a login. Open IG Share and tap "
                                + "\"Connect Instagram\" once, then try again.";
                    }
                    showError(msg);
                }
            }
        });
    }

    private void shareFiles(IgFetcher.Result result, ArrayList<Uri> uris,
                            List<String> mimes) {
        Intent send;
        if (uris.size() == 1) {
            send = new Intent(Intent.ACTION_SEND);
            send.setType(mimes.get(0));
            send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            send = new Intent(Intent.ACTION_SEND_MULTIPLE);
            boolean allVideo = !mimes.contains("image/jpeg");
            boolean allImage = !mimes.contains("video/mp4");
            send.setType(allVideo ? "video/*" : allImage ? "image/*" : "*/*");
            send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        // Caption travels with the media where the receiving app supports it
        StringBuilder text = new StringBuilder();
        if (!result.username.isEmpty()) text.append("@").append(result.username);
        if (!result.caption.isEmpty()) {
            if (text.length() > 0) text.append(": ");
            String cap = result.caption.trim();
            text.append(cap.length() > 500 ? cap.substring(0, 500) : cap);
        }
        if (text.length() > 0) send.putExtra(Intent.EXTRA_TEXT, text.toString());

        ClipData clip = ClipData.newUri(getContentResolver(), "IG media", uris.get(0));
        for (int i = 1; i < uris.size(); i++) {
            clip.addItem(new ClipData.Item(uris.get(i)));
        }
        send.setClipData(clip);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(send, "Send to…");
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(chooser);
        finish();
    }

    private void setStatus(String msg) {
        runOnUiThread(() -> status.setText(msg));
    }

    private void showError(String msg) {
        runOnUiThread(() -> {
            progress.setVisibility(View.GONE);
            status.setText(msg);
            btnCancel.setText("Close");
        });
    }
}
