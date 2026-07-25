package com.eftabsprodns.aio.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.lang.reflect.Field;

import com.eftabsprodns.aio.MyApplication;
import com.eftabsprodns.aio.config.ConfigUtil;
import com.eftabsprodns.aio.config.SettingsConstants;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Random;
import com.eftabsprodns.aio.service.SSHTunnelService;


public class util implements SettingsConstants {
    public static String str = new String(new byte[]{85, 83, 69, 82, 95, 67, 79, 73, 78, 83,});
    public static String a = new String(new byte[]{48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 72, 65, 82, 76, 73, 69,});
    private static SharedPreferences mPref;
    private static SharedPreferences.Editor mEditor;
    private static Context mContext;
    private static ConfigUtil mConfig;
    
    
    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    public util(Context c) {
        mContext = c;
        mPref = MyApplication.getPrivateSharedPreferences();
        mEditor = mPref.edit();
        mConfig = ConfigUtil.getInstance(c);
        // showProgrssDialog = new AlertDialog.Builder(c,mConfig.alertdialog()).create();
    }

    private static Context mContext() {
        if (mContext == null) {
            return MyApplication.getApp();
        }
        return mContext;
    }

    public static void showSnackInfo(int icon, String title, String subtitle, AppCompatActivity c) {
        /*final Snackbar snackbar = Snackbar.make(c.findViewById(android.R.id.content),"",Snackbar.LENGTH_LONG);
        View custom_view = c.getLayoutInflater().inflate(R.layout.snackbar, null);
        snackbar.getView().setBackgroundColor(Color.TRANSPARENT);
        Snackbar.SnackbarLayout snackBarView = (Snackbar.SnackbarLayout) snackbar.getView();
        snackBarView.setPadding(0, 0, 0, 0);
        int a = mPref.getBoolean("isAppThemeDark", false)?c.getResources().getColor(R.color.black_light):c.getResources().getColor(R.color.notif_light);
        ((CardView)custom_view.findViewById(R.id.snack_bg)).setCardBackgroundColor(a);
        TextView tv1 = custom_view.findViewById(R.id.snack_tv_title);
        TextView tv2 = custom_view.findViewById(R.id.snack_tv_subtitle);
        TextView tv3 = custom_view.findViewById(R.id.snack_tv_dismiss);
        ImageView iv = custom_view.findViewById(R.id.snack_image);
        iv.setImageResource(icon);
        iv.setColorFilter(mConfig.getColorAccent(), PorterDuff.Mode.SRC_IN);
        tv1.setText(title);
        tv1.setTextColor(mConfig.getTxtColor());
        tv2.setTextColor(mConfig.getTxtColorHint());
        tv2.setText(subtitle);
        tv3.setTextColor(mConfig.getColorAccent());
        tv3.setOnClickListener(v -> snackbar.dismiss());
        snackBarView.addView(custom_view, 0);
        snackbar.show();*/
    }

    //private static AlertDialog showProgrssDialog = null;
    public static void hideProgrss() {
        // if(showProgrssDialog!=null)if(showProgrssDialog.isShowing())showProgrssDialog.dismiss();
    }

    public static void showProgrss(String msg) {
       /* try{
            if(showProgrssDialog!=null)if(showProgrssDialog.isShowing())showProgrssDialog.dismiss();
            View inflate = LayoutInflater.from(mContext()).inflate(R.layout.progress_, null);
            showProgrssDialog = new AlertDialog.Builder(mContext(),mConfig.alertdialog()).create();
            TextView t = inflate.findViewById(R.id.progressTitle);
            ProgressBar i = inflate.findViewById(R.id.progress);
            TextView h = inflate.findViewById(R.id.dialog_hide);
            i.setIndeterminateTintList(ColorStateList.valueOf(mConfig.getColorAccent()));
            i.setProgress(100);
            t.setText(msg);
            t.setTextColor(mConfig.getTxtColor());
            h.setTextColor(mConfig.getColorAccent());
            h.setOnClickListener(p1 -> showProgrssDialog.dismiss());
            showProgrssDialog.setView(inflate);
            showProgrssDialog.setCancelable(true);
            showProgrssDialog.show();
        }catch (Exception e){
            TkLogStatus.logDebug(e.getMessage());
        }*/
    }

    /* public static boolean isMyApp(){
         if(!mContext().getString(R.string.app_name).equals(FileUtils.showJson("v6eIua7LdN9SHfiQnEXzOGqQqa+pJo9yLp5cCQ=="))){
             return false;
         }else if(!mContext().getPackageName().equals(FileUtils.showJson("UTBUSrfS7ppsPtnpRGHidS8zZQ0Md0vFHORIQTcUQOPa/TDYKLKp3hizdBeveKbRF/SP2Q=="))){
             return false;
         }else if(!mContext().getString(R.string.adunit_appID).equals(FileUtils.showJson("4IawPWqhh9+IPkCp68Phnczc5uhXjuIod6EjLxCFbO1rv2wibbq4iE3/PKskVksC+CvQk+HsKxdXP2YzJGCPAO8HX0tXCjvZGgzcEyYJ0tI5POQXTOZPGaL3LHcE6aAInOsmGT/zQPvm4yAtCqBjhAUekYvcYGSr"))){
             return false;
         }else if(!mContext().getString(R.string.adunit_banner).equals(FileUtils.showJson("vCJfmWJ4xGWXtV9wHuv8h3KhjfTdmvfyToZz1BpRKUpDUqEMToxjCSYgeOI4DSb/bmTVuPk/DTQ7zun5v5XsDW9Guu+aDihCaqznEnOwiXz33SJR2gc5Z6ELj4fWuj9rkmH9ab25lA41byxj1VFKN8TpfnkEXgcW"))){
             return false;
         }else if(!mContext().getString(R.string.adunit_interstitial).equals(FileUtils.showJson("puAKn19UcO3NZcvtGrouAzD7R2vfewejJ5OLXGBt1lsglv5YZVdWpeKjXE5ztGEh/U449j6EDo3SfT4ciHNL5ObQqQ+lQQH2BkQgxwcQy3ONbCx818IgHlQrkL7tG4aVjmI6L+vSjxlyqoB1rkimb9kWyXetBLvd"))){
             return false;
         }else if(!mContext().getString(R.string.adunit_rewarded).equals(FileUtils.showJson("atFJdxZkwepD7nEdBQmVpPvE6o2WmynjAhgeKUIo+j56Kpyr0csgHfQme6fwSVx9WW1VFpZPRFnXCEH7eJaS77wBE3co4t88C03CZlvS2b/MDvrRTG3ENDVEzwmYT3he9Rn95EPtqbCC5MC8or4o7b1vixdA/82t"))){
             return false;
         }else if(!mContext().getString(R.string.adunit_app_open).equals(FileUtils.showJson("qY5q5kvxBvZzCG5ZhgMgP7G5as2JkbK9hQmG6CfYWOIlbbUYjldCkIE4DeExZNOc+NWRNNN2zNgLcd/wHSYv7M/GhhQtdNBjYGziDIrjEwm+WkMtrkb/OiZgm1jT19cohkUX/9qhFrDQCzVKqV68ZSaS6Y3gBCmn"))){
             return false;
         }
         return true;
     }
    
    
 */
    
   public static void exitAll(Activity activity) {
		Intent stopTunnel = new Intent(SSHTunnelService.START_SSH_SERVICE);
		LocalBroadcastManager.getInstance(activity).sendBroadcast(stopTunnel);

		if (Build.VERSION.SDK_INT >= 16) {
			activity.finishAffinity();
		}
		System.exit(0);
	}
    
    
    public static String pw_repl(String user, String pw) {
        return pw;
    }

    public static boolean isSniffer(Context mContext) {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
        } else {
            NetworkInfo info = cm.getNetworkInfo(ConnectivityManager.TYPE_VPN);
            return (info != null && info.isConnectedOrConnecting());
        }
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        return info != null && info.isAvailable() && info.isConnected();
    }

    public void overrideFont(String defaultFontNameToOverride, String customFontFileNameInAssets) {
        try {
            final Typeface customFontTypeface = Typeface.createFromAsset(mContext().getAssets(), customFontFileNameInAssets);

            final Field defaultFontTypefaceField = Typeface.class.getDeclaredField(defaultFontNameToOverride);
            defaultFontTypefaceField.setAccessible(true);
            defaultFontTypefaceField.set(null, customFontTypeface);
        } catch (Exception e) {
            Toast.makeText(mContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


}
   
