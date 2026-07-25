/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package app.tunnel.vpncommons.vpnstatus;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.format.DateFormat;


import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.FormatFlagsConversionMismatchException;
import java.util.Locale;
import java.util.UnknownFormatConversionException;

import app.tunnel.vpncommons.R;

/**
 * Created by arne on 24.04.16.
 */
public class LogItem implements Parcelable {

    private Object[] mArgs = null;
    private String mMessage = null;
    private int mResourceId;
    // Default log priority
    TkLogStatus.LogLevel mLevel = TkLogStatus.LogLevel.INFO;
    private long logtime = System.currentTimeMillis();
    private int mVerbosityLevel = -1;

    public LogItem(int resId, Object... args) {
        mResourceId = resId;
        mArgs = args;
    }

    public LogItem(TkLogStatus.LogLevel loglevel, int verblevel, String msg) {
        mLevel = loglevel;
        mMessage = msg;
        mVerbosityLevel = verblevel;
    }

    public LogItem(TkLogStatus.LogLevel level, int resId, Object... args) {
        mLevel = level;
        mResourceId = resId;
        mArgs = args;
    }

    public LogItem(TkLogStatus.LogLevel loglevel, String msg) {
        mLevel = loglevel;
        mMessage = msg;
    }


    public LogItem(TkLogStatus.LogLevel loglevel, int ressourceId) {
        mResourceId = ressourceId;
        mLevel = loglevel;
    }

    @Override
    public String toString() {
        return getString(null);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeArray(mArgs);
        dest.writeString(mMessage);
        dest.writeInt(mResourceId);
        dest.writeInt(mLevel.getInt());
        dest.writeLong(logtime);
    }

    public LogItem(Parcel in) {
        mArgs = in.readArray(Object.class.getClassLoader());
        mMessage = in.readString();
        mResourceId = in.readInt();
        mLevel = TkLogStatus.LogLevel.getEnumByValue(in.readInt());
        logtime = in.readLong();
    }

    public static final Creator<LogItem> CREATOR
    = new Creator<LogItem>() {
        public LogItem createFromParcel(Parcel in) {
            return new LogItem(in);
        }

        public LogItem[] newArray(int size) {
            return new LogItem[size];
        }
    };

    public TkLogStatus.LogLevel getLogLevel() {
        return mLevel;
    }

    public long getLogtime() {
        return logtime;
    }

    public String getMessage() {
        return mMessage;
    }

    public String getString(Context c) {
        try {
            if (mMessage != null) {
                return mMessage;
            } else {
                if (c != null) {
                    if (mResourceId == R.string.app_mobile_info)
                        return getAppInfoString(c);
                    else if (mArgs == null)
                        return c.getString(mResourceId);
                    else
                        return c.getString(mResourceId, mArgs);
                } else {
                    String str = String.format(Locale.ENGLISH, "Log (no context) resid %d", mResourceId);
                    if (mArgs != null)
                        str += join("|", mArgs);

                    return str;
                }
            }
        } catch (UnknownFormatConversionException e) {
            if (c != null)
                throw new UnknownFormatConversionException(e.getLocalizedMessage() + getString(null));
            else
                throw e;
        } catch (FormatFlagsConversionMismatchException e) {
            if (c != null)
                throw new FormatFlagsConversionMismatchException(e.getLocalizedMessage() + getString(null), e.getConversion());
            else
                throw e;
        }
    }

    //private String listb = "";

    // The lint is wrong here
    @SuppressLint("StringFormatMatches")
    private String getAppInfoString(Context c) {
        c.getPackageManager();
        String apksign = "error getting package signature";

        String version = "error getting version";
        try {
            @SuppressLint("PackageManagerGetSignatures")
                Signature raw = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES).signatures[0];
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(raw.toByteArray()));
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] der = cert.getEncoded();
            md.update(der);
            byte[] digest = md.digest();
            
            if (!Arrays.equals(digest, TkLogStatus.oficialkey) && !Arrays.equals(digest, TkLogStatus.oficialdebugkey))
                apksign = "- Core3";
            else
                apksign = "";

            PackageInfo packageinfo = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            version = String.format("%s Build %d", packageinfo.versionName, packageinfo.versionCode);

        } catch (PackageManager.NameNotFoundException | CertificateException |
        NoSuchAlgorithmException ignored) {
        }
        return c.getString(R.string.app_mobile_info, version, apksign);
    }

    // TextUtils.join will cause not macked exeception in tests ....
    public static String join(CharSequence delimiter, Object[] tokens) {
        StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (Object token : tokens) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(delimiter);
            }
            sb.append(token);
        }
        return sb.toString();
    }

    public int getVerbosityLevel() {
        if (mVerbosityLevel == -1) {
            // Hack:
            // For message not from OpenVPN, report the status level as log level
            return mLevel.getInt();
        }
        return mVerbosityLevel;
    }
    
    public String format(Context context, int timeFormat) {
        String pfx = "";
        if (timeFormat != 0) {
            Date d = new Date(getLogtime());
            java.text.DateFormat formatter;
            if (timeFormat==2) { 
                formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault());
            } else {
                formatter = DateFormat.getTimeFormat(context);
            }
            pfx = "<b>["+formatter.format(d)+"]</b> " + " ";
        }
        return pfx + getString(context);
    }
    
}

