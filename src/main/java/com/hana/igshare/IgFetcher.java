package com.hana.igshare;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches public Instagram media (single image/video, reels, and every
 * page of a carousel) straight from Instagram — no login, no account.
 *
 * Routes are tried in order until one returns media:
 *  1. The mobile API info endpoint on i.instagram.com (full carousels,
 *     best quality) with the header set the real app sends.
 *  2. The public embed page for the post.
 *  3. The post page itself (Open Graph tags + embedded JSON).
 *  4. A public embed mirror (ddinstagram) Open Graph tags.
 * Instagram blocks these routes intermittently, so the chain matters.
 */
public class IgFetcher {

    public static class Item {
        public final String url;
        public final boolean video;

        Item(String url, boolean video) {
            this.url = url;
            this.video = video;
        }

        public String ext() {
            return video ? "mp4" : "jpg";
        }
    }

    public static class Result {
        public final List<Item> items = new ArrayList<>();
        public String caption = "";
        public String username = "";
    }

    public static class FetchException extends Exception {
        public FetchException(String m) {
            super(m);
        }
    }

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final Pattern LINK = Pattern.compile(
            "(?:instagram\\.com|ddinstagram\\.com)/(?:p|reel|reels|tv)/([A-Za-z0-9_\\-]+)");
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final String APP_UA =
            "Instagram 275.0.0.27.98 Android (33/13; 480dpi; 1080x2400; "
                    + "samsung; SM-S928W; e3q; qcom; en_US)";
    private static final String DEVICE_ID = UUID.randomUUID().toString();

    public static String extractShortcode(String text) {
        if (text == null) return null;
        Matcher m = LINK.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    static String mediaId(String shortcode) {
        BigInteger n = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(64);
        for (char c : shortcode.toCharArray()) {
            int i = ALPHABET.indexOf(c);
            if (i < 0) continue;
            n = n.multiply(base).add(BigInteger.valueOf(i));
        }
        return n.toString();
    }

    public static Result fetch(android.content.Context ctx, String shortcode)
            throws FetchException {
        StringBuilder errs = new StringBuilder();
        try {
            Result r = fetchViaApi(ctx, shortcode);
            if (!r.items.isEmpty()) return r;
            errs.append("api: no media; ");
        } catch (Exception e) {
            errs.append("api: ").append(shortMsg(e)).append("; ");
        }
        try {
            Result r = fetchViaEmbed(shortcode);
            if (!r.items.isEmpty()) return r;
            errs.append("embed: no media; ");
        } catch (Exception e) {
            errs.append("embed: ").append(shortMsg(e)).append("; ");
        }
        try {
            Result r = fetchViaPostPage(shortcode);
            if (!r.items.isEmpty()) return r;
            errs.append("page: no media; ");
        } catch (Exception e) {
            errs.append("page: ").append(shortMsg(e)).append("; ");
        }
        try {
            Result r = fetchViaDd(shortcode);
            if (!r.items.isEmpty()) return r;
            errs.append("mirror: no media");
        } catch (Exception e) {
            errs.append("mirror: ").append(shortMsg(e));
        }
        throw new FetchException("Couldn't get this post. It may be private, "
                + "or Instagram is rate-limiting right now — try again in a bit.\n("
                + errs + ")");
    }

    // --- Route 1: mobile API info endpoint ---

    private static Result fetchViaApi(android.content.Context ctx, String shortcode)
            throws Exception {
        String id = mediaId(shortcode);
        Exception last = null;
        for (String host : new String[]{"i.instagram.com", "www.instagram.com"}) {
            try {
                return fetchViaApiHost(ctx, host, id);
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new FetchException("api failed");
    }

    private static Result fetchViaApiHost(android.content.Context ctx, String host,
                                          String id) throws Exception {
        String url = "https://" + host + "/api/v1/media/" + id + "/info/";
        HttpURLConnection conn = open(url);
        conn.setRequestProperty("User-Agent", APP_UA);
        conn.setRequestProperty("X-IG-App-ID", "936619743392459");
        conn.setRequestProperty("X-IG-Device-ID", DEVICE_ID);
        conn.setRequestProperty("X-IG-Capabilities", "3brTvx0=");
        conn.setRequestProperty("X-IG-Connection-Type", "WIFI");
        conn.setRequestProperty("Accept-Language", "en-US");
        conn.setRequestProperty("Accept", "application/json");
        String cookie = IgSession.cookieHeader(ctx);
        if (cookie != null) {
            conn.setRequestProperty("Cookie", cookie);
            String csrf = IgSession.csrf(ctx);
            if (csrf != null && !csrf.isEmpty()) {
                conn.setRequestProperty("X-CSRFToken", csrf);
            }
        }
        String body = readBody(conn);
        if (body == null || !body.trim().startsWith("{")) {
            throw new FetchException("non-JSON response");
        }
        JSONObject root = new JSONObject(body);
        JSONArray items = root.getJSONArray("items");
        JSONObject post = items.getJSONObject(0);

        Result r = new Result();
        JSONObject user = post.optJSONObject("user");
        if (user != null) r.username = user.optString("username", "");
        JSONObject cap = post.optJSONObject("caption");
        if (cap != null) r.caption = cap.optString("text", "");

        JSONArray carousel = post.optJSONArray("carousel_media");
        if (carousel != null && carousel.length() > 0) {
            for (int i = 0; i < carousel.length(); i++) {
                addBest(carousel.getJSONObject(i), r.items);
            }
        } else {
            addBest(post, r.items);
        }
        return r;
    }

    private static void addBest(JSONObject media, List<Item> out) {
        JSONArray videos = media.optJSONArray("video_versions");
        if (videos != null && videos.length() > 0) {
            JSONObject best = widest(videos);
            if (best != null) {
                out.add(new Item(best.optString("url"), true));
                return;
            }
        }
        JSONObject iv = media.optJSONObject("image_versions2");
        if (iv != null) {
            JSONArray cands = iv.optJSONArray("candidates");
            if (cands != null && cands.length() > 0) {
                JSONObject best = widest(cands);
                if (best != null) out.add(new Item(best.optString("url"), false));
            }
        }
    }

    private static JSONObject widest(JSONArray arr) {
        JSONObject best = null;
        int bestW = -1;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || o.optString("url", "").isEmpty()) continue;
            int w = o.optInt("width", 0);
            if (w > bestW) {
                bestW = w;
                best = o;
            }
        }
        return best;
    }

    // --- Route 2: public embed page ---

    private static Result fetchViaEmbed(String shortcode) throws Exception {
        String url = "https://www.instagram.com/p/" + shortcode + "/embed/captioned/";
        HttpURLConnection conn = open(url);
        conn.setRequestProperty("User-Agent", DESKTOP_UA);
        String html = readBody(conn);
        if (html == null) throw new FetchException("empty response");
        return parseMediaFromHtml(html);
    }

    // --- Route 3: the post page itself ---

    private static Result fetchViaPostPage(String shortcode) throws Exception {
        String url = "https://www.instagram.com/p/" + shortcode + "/";
        HttpURLConnection conn = open(url);
        conn.setRequestProperty("User-Agent", DESKTOP_UA);
        conn.setRequestProperty("Accept-Language", "en-US");
        String html = readBody(conn);
        if (html == null) throw new FetchException("empty response");
        return parseMediaFromHtml(html);
    }

    // --- Route 4: ddinstagram Open Graph ---

    private static Result fetchViaDd(String shortcode) throws Exception {
        String url = "https://ddinstagram.com/p/" + shortcode;
        HttpURLConnection conn = open(url);
        conn.setRequestProperty("User-Agent", DESKTOP_UA);
        String html = readBody(conn);
        if (html == null) throw new FetchException("empty response");
        return parseMediaFromHtml(normalize(html));
    }

    /**
     * Pull media URLs out of a page's HTML. Instagram escapes URLs inside
     * its JSON blobs (https:\/\/…\u0026…), so normalize first, then look
     * for Open Graph tags, a video_url JSON field, and raw CDN media
     * links in page order.
     */
    static Result parseMediaFromHtml(String rawHtml) {
        String html = normalize(rawHtml);
        Result r = new Result();

        String ogVideo = findOg(html, "og:video");
        if (ogVideo == null) ogVideo = findOg(html, "og:video:url");
        if (ogVideo != null && !ogVideo.isEmpty()) {
            r.items.add(new Item(ogVideo, true));
            return r;
        }

        String vu = findJsonString(html, "video_url");
        if (vu != null && !vu.isEmpty()) {
            r.items.add(new Item(vu, true));
            return r;
        }

        // CDN media in page order. Post media lives under /t51.2885-15/
        // (images) or as .mp4 links; profile pics use -19, skip those.
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = Pattern.compile("https://[^\"'\\s]+cdninstagram\\.com[^\"'\\s]*")
                .matcher(html);
        while (m.find()) {
            String u = m.group();
            if (u.contains("/t51.2885-19/")) continue; // profile pictures
            boolean isVideo = u.contains(".mp4");
            boolean isPostImage = u.contains("/t51.2885-15/");
            if (!isVideo && !isPostImage) continue;
            if (seen.add(u)) r.items.add(new Item(u, isVideo));
        }
        if (!r.items.isEmpty()) return r;

        String du = findJsonString(html, "display_url");
        if (du != null && !du.isEmpty()) {
            r.items.add(new Item(du, false));
            return r;
        }
        String ogImage = findOg(html, "og:image");
        if (ogImage != null && !ogImage.isEmpty()) {
            r.items.add(new Item(ogImage, false));
        }
        return r;
    }

    /** De-escape the forms Instagram/HTML use inside page source. */
    static String normalize(String s) {
        return s.replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("\\u002F", "/")
                .replace("\\u002f", "/")
                .replace("&amp;", "&")
                .replace("\\\"", "\"");
    }

    private static String findOg(String html, String property) {
        Matcher m1 = Pattern.compile(
                "property=\"" + property + "\"[^>]*content=\"([^\"]+)\"").matcher(html);
        if (m1.find()) return unescapeHtml(m1.group(1));
        Matcher m2 = Pattern.compile(
                "content=\"([^\"]+)\"[^>]*property=\"" + property + "\"").matcher(html);
        return m2.find() ? unescapeHtml(m2.group(1)) : null;
    }

    private static String findJsonString(String text, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(text);
        return m.find() ? unescapeJson(m.group(1)) : null;
    }

    static String unescapeJson(String s) {
        return s.replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("\\u003d", "=")
                .replace("\\u0025", "%")
                .replace("\\\"", "\"")
                .replace("\\n", " ")
                .replace("\\\\", "\\");
    }

    static String unescapeHtml(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    // --- HTTP plumbing ---

    private static HttpURLConnection open(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    private static String readBody(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300
                ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) throw new IOException("HTTP " + code);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
        }
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        return sb.toString();
    }

    /** Downloads one media file; returns bytes written. */
    public static long download(String urlStr, File dest) throws IOException {
        HttpURLConnection conn = open(urlStr);
        conn.setRequestProperty("User-Agent", DESKTOP_UA);
        conn.setRequestProperty("Referer", "https://www.instagram.com/");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        long total = 0;
        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                total += n;
            }
        }
        return total;
    }

    private static String shortMsg(Exception e) {
        String m = e.getMessage();
        if (m == null) m = e.getClass().getSimpleName();
        return m.length() > 60 ? m.substring(0, 60) : m;
    }
}
