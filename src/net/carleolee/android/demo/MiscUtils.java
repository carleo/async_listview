package net.carleolee.android.demo;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

/**
 * Digest utility
 */
public class MiscUtils {

    static final char[] HEX_CHARS = {
        '0','1','2','3','4','5','6','7','8','9',
        'a','b','c','d','e','f'
    };

    /** get hex string of specified bytes */
    public static String toHexString(byte[] bytes, int off, int len) {
        if (bytes == null)
            throw new NullPointerException("bytes is null");
        if (off < 0 || (off + len) > bytes.length)
            throw new IndexOutOfBoundsException();
        char[] buff = new char[len * 2];
        int v;
        int c = 0;
        for (int i = 0; i < len; i++) {
            v = bytes[i+off] & 0xff;
            buff[c++] = HEX_CHARS[(v >> 4)];
            buff[c++] = HEX_CHARS[(v & 0x0f)];
        }
        return new String(buff, 0, len * 2);
    }

    /** get hexadecimal md5 digest of given string (its UTF-8 encoded bytes) */
    public static String md5Hex(String str) {
        try {
            if (str == null || str.length() == 0)
                return null;
            MessageDigest digester = MessageDigest.getInstance("MD5");
            if (digester == null)
                return null;
            byte[] data = str.getBytes("UTF-8");
            digester.update(data);
            byte[] d = digester.digest();
            if (d == null || d.length < 1)
                return null;
            return toHexString(d, 0, d.length);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isSdcardAvailable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state));
    }

    public static boolean isSdcardWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    public static String getCacheDir(Context context) {
        String pkgName = context.getPackageName();
        return Environment.getExternalStorageDirectory().getPath() +
                "/Android/data/" + pkgName + "/cache";
    }

    public static boolean saveIcon(byte[] data, int length, String dir, String name) {
        if (!isSdcardWritable())
            return false;
        FileOutputStream out = null;
        try {
            File d = new File(dir);
            if (!d.exists()) {
                if (!d.mkdirs())
                    return false;
            } else if (!d.isDirectory()) {
                return false;
            }
            String fname = dir + "/" + name + ".dat";
            out = new FileOutputStream(fname);
            out.write(data);
            return true;
        } catch (Exception e) {
            // ignore
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ex) {
                    // ignore
                }
            }
        }
        return false;
    }

    public static Bitmap loadIcon(String dir, String name) {
        if (!isSdcardAvailable())
            return null;
        try {
            String fname = dir + "/" + name + ".dat";
            Bitmap bm = BitmapFactory.decodeFile(fname);
            return bm;
        } catch (Exception e) {
            return null;
        }
    }

    public static int downloadIcon(String urlstr, byte[] buff, int maxSize) {
        if (maxSize <= 0)
            return 0;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlstr);
            URLConnection connection = url.openConnection();
            if (!(connection instanceof HttpURLConnection)) {
                return -1;
            }
            conn = (HttpURLConnection) connection;
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();
            int length = conn.getContentLength();
            if (length > maxSize)
                return -1;
            InputStream in = null;
            in = new BufferedInputStream(conn.getInputStream(), 8 * 1024);
            int off = 0;
            int len = maxSize;
            while (len > 0) {
                int count = in.read(buff, off, len);
                if (count == -1) {
                    break;
                } else if (count == 0) {
                    return -1;
                } else {
                    off += count;
                    len -= count;
                    if (len <= 0) {
                        return -1;
                    }
                }
            }
            in.close();
            return off;
        } catch (Exception e) {
            return -1;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception e4) {
                    // ignore
                }
            }
        }
    }

    public static void clearCache(String dir) {
        if (!isSdcardAvailable())
            return;
        try {
            File d = new File(dir);
            if (!d.exists() || !d.isDirectory())
                return;
            File[] list = d.listFiles();
            if (list == null || list.length == 0)
                return;
            for (File file: list) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
