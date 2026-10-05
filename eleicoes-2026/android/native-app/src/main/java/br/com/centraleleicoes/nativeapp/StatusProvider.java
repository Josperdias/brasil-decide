package br.com.centraleleicoes.nativeapp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Entrega as imagens de status geradas (cache/share) para o app de destino, só leitura. */
public class StatusProvider extends ContentProvider {
    static final String AUTHORITY = "br.com.centraleleicoes.nativeapp.share";

    private File dir() { return new File(getContext().getCacheDir(), "share"); }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) { return "image/png"; }

    private File resolve(Uri uri) throws FileNotFoundException {
        try {
            File f = new File(dir(), uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment()).getCanonicalFile();
            if (!f.getPath().startsWith(dir().getCanonicalPath() + File.separator) || !f.isFile()) throw new FileNotFoundException();
            return f;
        } catch (java.io.IOException e) { throw new FileNotFoundException(); }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("somente leitura");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String sel, String[] args, String order) {
        try {
            File f = resolve(uri);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (FileNotFoundException e) { return null; }
    }

    @Override public Uri insert(Uri uri, ContentValues v) { throw new UnsupportedOperationException(); }

    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }

    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
