package com.kaisar.xposed.godmode;


import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.kaisar.xposed.godmode.injection.bridge.GodModeManager;

public class SettingsActivity extends AppCompatActivity {

    private boolean mNoticeStatus = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
    }

    @Override
    protected void onStart() {
        super.onStart();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);
        //startNotificationService();

        startService(new Intent(this, ViewRuleService.class));
    }

    private void startNotificationService() {
        Intent notificationService = new Intent(this, NotificationService.class);
        startService(notificationService);
    }

    public void setNoticeStatus(boolean pEnable) {
        if (pEnable != this.mNoticeStatus) {
            Intent notificationService = new Intent(this, NotificationService.class);
            if (pEnable) startService(notificationService);
            else stopService(notificationService);
            this.mNoticeStatus = pEnable;
        }
    }

}
