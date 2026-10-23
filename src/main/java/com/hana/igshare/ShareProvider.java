package com.hana.igshare;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Minimal FileProvider-style content provider: serves files from the
 * app's cache "shares" folder so WhatsApp/Telegram/etc. can read the
 * downloaded media through granted content:// URIs.
 */
public class ShareProvider extends ContentProvider {

    static File sharesDir(android.content.Context ctx) {
        File dir = new File(ctx.getCacheDir(), "shares");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static Uri uriFor(File f) {
        return new Uri.Builder()
                .scheme("content")
                .authority("com.hana.igshare.files")
                .appendPath(f.getName())
                .build();
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        String name = new File(uri.getLastPathSegment()).getName(); // basename only
        File f = new File(sharesDir(getContext()), name);
        try {
            String parent = sharesDir(getContext()).getCanonicalPath();
            if (!f.getCanonicalPath().startsWith(parent)) {
                throw new FileNotFoundException("outside shares dir");
            }
        } catch (java.io.IOException e) {
            throw new FileNotFoundException("bad path");
        }
        if (!f.exists()) throw new FileNotFoundException(name);
        return f;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(uri),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        try {
            File f = fileFor(uri);
            MatrixCursor cursor = new MatrixCursor(new String[]{
                    OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            cursor.addRow(new Object[]{f.getName(), f.length()});
            return cursor;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        String ext = "";
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot >= 0) ext = name.substring(dot + 1).toLowerCase();
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime != null ? mime : "application/octet-stream";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
