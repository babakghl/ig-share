package com.hana.igshare;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Holds the Instagram session cookie captured by the in-app login
 * (LoginActivity). The password is entered on Instagram's own page in a
 * WebView and never touches this app; only the resulting session cookie
 * is kept, in app-private storage on this device only.
 */
public class IgSession {
    private static final String PREFS = "ig_session";
    private static final String K_SESSION = "sessionid";
    private static final String K_CSRF = "csrftoken";
    private static final String K_USER = "ds_user_id";

    public static void save(Context ctx, String sessionid, String csrftoken,
                            String userId) {
        sp(ctx).edit()
                .putString(K_SESSION, sessionid)
                .putString(K_CSRF, csrftoken)
                .putString(K_USER, userId)
                .apply();
    }

    public static void clear(Context ctx) {
        sp(ctx).edit().clear().apply();
    }

    public static boolean hasSession(Context ctx) {
        String s = sp(ctx).getString(K_SESSION, "");
        return s != null && !s.isEmpty();
    }

    public static String sessionId(Context ctx) {
        return sp(ctx).getString(K_SESSION, "");
    }

    public static String csrf(Context ctx) {
        return sp(ctx).getString(K_CSRF, "");
    }

    public static String cookieHeader(Context ctx) {
        if (!hasSession(ctx)) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("sessionid=").append(sessionId(ctx));
        String csrf = csrf(ctx);
        if (csrf != null && !csrf.isEmpty()) sb.append("; csrftoken=").append(csrf);
        String user = sp(ctx).getString(K_USER, "");
        if (user != null && !user.isEmpty()) sb.append("; ds_user_id=").append(user);
        return sb.toString();
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
