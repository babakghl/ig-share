# IG Share

Share Instagram posts, reels and carousels with people who aren't on Instagram — as real photos and videos in WhatsApp, Telegram, or any other chat app.

When you share an Instagram link to a friend who doesn't use Instagram, they normally get a dead end: a login wall. **IG Share** fixes that: it downloads the actual media — the full video, or **every page of a carousel** — and hands the files to your normal share sheet.

## Download

**[⬇ Download IGShare.apk (v1.2)](https://github.com/babakghl/ig-share/releases/download/v1.2/IGShare.apk)**

Requires Android 8.0+. ~30 KB, no ads, no tracking, no account of its own. Only permission: internet.

## How it works

1. In Instagram, open a post or reel → **Share** (or ⋯ → Share) → pick **IG Share**
2. The app downloads the video / all carousel pages in the background
3. A share sheet opens with the actual files → pick WhatsApp, Telegram, … → send

You can also copy a link, open IG Share, and paste it there.

## Connecting Instagram (one-time)

Instagram now blocks most downloads from logged-out requests, so IG Share asks you to connect once:

- Tap **Connect Instagram** and log in on **Instagram's own page** inside the app.
- Your password goes only to Instagram — the app never sees or stores it. It keeps just the **session cookie** Instagram issues (the same thing a browser keeps), stored in the app's private storage on your phone, and sent only to Instagram.
- **Disconnect** wipes it anytime. Changing your Instagram password kills the session everywhere, including here.

Works with public posts (and anything your account can see, once connected).

## Privacy

- No analytics, no ads, no third-party SDKs, no server of ours — your phone talks directly to Instagram.
- Downloaded files sit in the app's cache and are auto-deleted after 24 hours.

## Building

Pure Java + Android framework, no Gradle:

```sh
./build.sh   # needs JDK 17 and an Android SDK (platform 34)
```

The script runs aapt2 → javac → d8 → zipalign → apksigner and produces `dist/IGShare.apk`. Signing uses a local `debug.keystore` generated on first build (git-ignored — keep yours safe, Android requires the same key for updates).

## License

MIT — see [LICENSE](LICENSE).
