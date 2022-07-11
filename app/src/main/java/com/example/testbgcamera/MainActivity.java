package com.example.testbgcamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private final static int CODE_PERM_CAMERA = 0;
    private final static int CODE_PERM_SYSTEM_ALERT_WINDOW = 1;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (null != intent.getAction() && intent.getAction().equals(CameraService.ACTION_STOPPED)) {
                flipButtonVisibility(false);
            }
        }
    };

    private void flipButtonVisibility(boolean running) {
        View buttonStart = findViewById(R.id.buttontStart);
        View buttonStop = findViewById(R.id.buttonStop);
        buttonStart.setVisibility(running ? View.GONE : View.VISIBLE);
        buttonStop.setVisibility(!running ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        String permission = Manifest.permission.CAMERA;
        if (ContextCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED) {
            // We don't have camera permission yet. Request it from the user.
            ActivityCompat.requestPermissions(this, new String[]{permission}, CODE_PERM_CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mBroadcastReceiver, new IntentFilter(CameraService.ACTION_STOPPED));

        boolean running = isServiceRunning();
        flipButtonVisibility(running);

        if (CameraService.SHOW_PREVIEW && !Settings.canDrawOverlays(this)) {
            // Don't have permission to draw over other apps yet - ask user to give permission
            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(settingsIntent, CODE_PERM_SYSTEM_ALERT_WINDOW);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcastReceiver);
    }

    private void notifyService(String action) {
        Intent intent = new Intent(this, CameraService.class);
        intent.setAction(action);
        startService(intent);
    }

    private void initView() {
        View buttonStart = findViewById(R.id.buttontStart);
        View buttonStop = findViewById(R.id.buttonStop);
        buttonStart.setOnClickListener(v -> {
            if (!isServiceRunning()) {
                notifyService(CameraService.ACTION_START);
                finish();
            }
        });
        buttonStop.setOnClickListener(v -> stopService(new Intent(this, CameraService.class)));
    }

    private boolean isServiceRunning() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (CameraService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (CODE_PERM_CAMERA == requestCode) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                finish();
            }
        }
    }
}