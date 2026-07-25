package com.eftabsprodns.aio.activities;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.eftabsprodns.aio.MyApplication;
import com.tpv.plus.R;
import com.eftabsprodns.aio.utils.GoogleMobileAdsConsentManager;
import com.google.android.gms.ads.MobileAds;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

@SuppressLint("CustomSplashScreen")
public class SplashScreen extends AppCompatActivity {

    private static final String LOG_TAG = "SplashActivity";
    private final AtomicBoolean isMobileAdsInitializeCalled = new AtomicBoolean(false);
    private GoogleMobileAdsConsentManager googleMobileAdsConsentManager;
    private static final long COUNTER_TIME = 1;
	private long secondsRemaining;
	private Handler handler = new Handler();
    int sleep = 0;
	Handler handlerz = new Handler();    
    /**
     * Number of milliseconds to count down before showing the app open ad. This simulates the time
     * needed to load the app.
     */
    private static final long COUNTER_TIME_MILLISECONDS = 5000;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash);

        // Create a timer so the SplashActivity will be displayed for a fixed amount of time.
        createTimer(COUNTER_TIME);

    }

    /**
     * Create the countdown timer, which counts down to zero and show the app open ad.
     *
     * @param time the number of milliseconds that the timer counts down from
     */
   

    
    private void createTimer(long seconds) {
		//final TextView counterTextView = findViewById(R.id.timer);

		CountDownTimer countDownTimer =
			new CountDownTimer(seconds * 1000, 500) {
			@Override
			public void onTick(long millisUntilFinished) {
				secondsRemaining = ((millisUntilFinished / 1000) + 1);
				//counterTextView.setText("App is done loading in: " + secondsRemaining);
			}

			@Override
			public void onFinish() {
				secondsRemaining = 0;
				//counterTextView.setText("Done.");

				Application application = getApplication();

				// If the application is not an instance of MyApplication, log an error message and
				// start the MainActivity without showing the app open ad.
				if (!(application instanceof MyApplication)) {
					//Toast.makeText(LauncherActivity.this, "hoy inatay", Toast.LENGTH_SHORT).show();
					new Handler(Looper.getMainLooper()).postDelayed(new Runnable(){

							@Override
							public void run() {
								Intent intent = new Intent(SplashScreen.this, OpenVPNClient.class);
                                startActivity(intent);
                                finish();
							}
						}, 1000);
					return;
				}

				// Show the app open ad.
				((MyApplication) application)
					.showAdIfAvailable(SplashScreen.this,new MyApplication.OnShowAdCompleteListener() {
						@Override
						public void onShowAdComplete() {
							new Handler(Looper.getMainLooper()).postDelayed(new Runnable(){

									@Override
									public void run() {
										Intent intent = new Intent(SplashScreen.this, OpenVPNClient.class);
                                        startActivity(intent);
                                        finish();
									}
								}, 1000);
							//Toast.makeText(LauncherActivity.this, "hoy inatay2", Toast.LENGTH_SHORT).show();
						}
                    });
			}
        };
		countDownTimer.start();
	}

    /** Start the MainActivity. */
    public void startMainActivity() {
        Intent intent = new Intent(this, OpenVPNClient.class);
        this.startActivity(intent);
    }

}


