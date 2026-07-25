package com.eftabsprodns.aio.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Scanner;

import app.tunnel.vpncommons.vpnstatus.TkLogStatus;
import com.eftabsprodns.aio.MyApplication;
import com.tpv.plus.R;
import com.eftabsprodns.aio.utils.de.De;
import com.eftabsprodns.aio.utils.de.De2;
import com.eftabsprodns.aio.utils.en.En;
import com.eftabsprodns.aio.utils.en.En2;


public class FileUtils {

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    public static boolean copyToClipboard(Context context, String text) {
        try {
            int sdk = android.os.Build.VERSION.SDK_INT;
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(context.CLIPBOARD_SERVICE);
            if (sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
                clipboard.setText(text);
            } else {
                android.content.ClipData clip = android.content.ClipData.newPlainText("Message", text);
                clipboard.setPrimaryClip(clip);
            }
            return (clipboard != null) ? true : false;
        } catch (Exception e) {
            TkLogStatus.logDebug(e.getMessage());
            return false;
        }
    }

    public static String readFromAsset(final AppCompatActivity c) {
        try {
            File file = new File(c.getFilesDir(), "tknetwork.hs");
            StringBuilder b = new StringBuilder();
            Reader reader = null;
            char[] buff = new char[1024];
            if (file.exists()) {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            } else {
                reader = new BufferedReader(new InputStreamReader(c.getAssets().open("tknetwork.hs")));
            }
            while (true) {
                int read = reader.read(buff, 0, buff.length);
                if (read <= 0) {
                    break;
                }
                b.append(buff, 0, read);
            }
            return showJson(b.toString());
        } catch (Exception e) {
            Toast.makeText(c, "readFromAsset error! " + e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    public static String readFromRaw(Context context, int resId) {
        InputStream in = context.getResources().openRawResource(resId);
        Scanner scanner = new Scanner(in, "UTF-8").useDelimiter("\\A");
        StringBuilder sb = new StringBuilder();
        while (scanner.hasNext()) {
            sb.append(scanner.next());
        }
        scanner.close();
        return sb.toString();
    }

    public static String readTextFile(File f) {
        StringBuilder text = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(f));
            String st;
            while ((st = br.readLine()) != null) {
                text.append(st + "\n");
            }
            br.close();
            return text.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String readTextUri(Context c, Uri uri) {
        BufferedReader reader = null;
        StringBuilder builder = new StringBuilder();
        try {
            reader = new BufferedReader(new InputStreamReader(c.getContentResolver().openInputStream(uri)));
            String line = "";
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return builder.toString();
    }

    private static String getPass() {
        String str = "6465787465722E65736B616C617274652E6D6564696174656B76706E2E6465782E7068";
        //String str = "78702I74756I6I656R2I76706I2I666173742I616I642I7265637572652I7068";

        return str.substring(0, 4);
    }

    public static String showJson(String msg) {
        try {
            String _de = XxTea.decryptBase64StringToString(msg, getPass());
            return _De(_de);
        } catch (Exception e) {
            return "error!";
        }
    }

    public static String hideJson(String msg) {
        try {
            String _en = _En(msg);
            return XxTea.encryptToBase64String(_en, getPass());
        } catch (Exception e) {
            return "error!";
        }
    }

    /*public static String getHyteriaJS(Context mContext){
        String content = showJson(readFromRaw(mContext, R.raw.mdocument));
        return content;
    }*/

    private static String _De(String msg) throws Exception {
        De de = new De();
        de.setMethod(new De2());
        int p = Integer.parseInt(getPass());
        String de1 = de.decryptString(msg, p);
        return de.decryptString(de1, p);
    }

    private static String _En(String msg) throws Exception {
        En encrypter = new En();
        encrypter.setMethod(new En2());
        int p = Integer.parseInt(getPass());
        String e1 = encrypter.encryptString(msg, p);
        return encrypter.encryptString(e1, p);
    }

    public static String readFile(String path, long max_len) throws IOException {
        return readStream(new FileInputStream(path), max_len, path);
    }

    public static String readUri(Context context, Uri uri, long max_len) throws IOException {
        return readStream(context.getContentResolver().openInputStream(uri), max_len, uriBasename(uri));
    }

    public static String readAsset(Context context, String filename) throws IOException {
        return readStream(context.getResources().getAssets().open(filename), 0, filename);
    }

    public static String readFileAppPrivate(Context context, String filename) throws IOException {
        return readStream(context.openFileInput(filename), 0, filename);
    }

    public static void writeFileAppPrivate(Context context, String filename, String content) throws IOException {
        FileOutputStream fos = context.openFileOutput(filename, 0);
        try {
            fos.write(content.getBytes());
        } finally {
            fos.close();
        }
    }

    public static String readStream(InputStream stream, long max_len, String fn) throws IOException {
        try {
            Reader reader = new BufferedReader(new InputStreamReader(stream));
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            while (true) {
                int read = reader.read(buffer, 0, buffer.length);
                if (read <= 0) {
                    break;
                }
                builder.append(buffer, 0, read);
                if (max_len > 0 && ((long) builder.length()) > max_len) {
                    throw new FileTooLarge(fn, max_len);
                }
            }
            String stringBuilder = builder.toString();
            return stringBuilder;
        } finally {
            stream.close();
        }
    }

    public static byte[] readFileByteArray(String path, long max_len) throws IOException {
        File file = new File(path);
        InputStream is = new FileInputStream(file);
        try {
            long length = file.length();
            if ((max_len <= 0 || length <= max_len) && length <= 2147483647L) {
                byte[] bytes = new byte[((int) length)];
                int offset = 0;
                while (offset < bytes.length) {
                    int numRead = is.read(bytes, offset, bytes.length - offset);
                    if (numRead < 0) {
                        break;
                    }
                    offset += numRead;
                }
                if (offset >= bytes.length) {
                    return bytes;
                }
                throw new IOException("Could not completely read file: " + path);
            }
            throw new FileTooLarge(path, max_len);
        } finally {
            is.close();
        }
    }

    public static boolean deleteFile(String path) {
        if (path != null) {
            return new File(path).delete();
        }
        return false;
    }

    public static boolean renameFile(String from_path, String to_path) {
        if (from_path == null || to_path == null) {
            return false;
        }
        return new File(from_path).renameTo(new File(to_path));
    }

    public static String basename(String path) {
        if (path != null) {
            return new File(path).getName();
        }
        return null;
    }

    public static String dirname(String path) {
        if (path != null) {
            return new File(path).getParent();
        }
        return null;
    }

    public static String uriBasename(Uri uri) {
        if (uri != null) {
            return uri.getLastPathSegment();
        }
        return null;
    }

    public static class FileTooLarge extends IOException {
        public FileTooLarge(String fn, long max_size) {
            super(String.format(MyApplication.resString(R.string.profile_too_large), new Object[]{fn, Long.valueOf(max_size)}));
        }
    }
}
