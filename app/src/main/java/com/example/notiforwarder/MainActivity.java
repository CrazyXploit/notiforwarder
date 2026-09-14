package com.example.notiforwarder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends Activity {

    private EditText botTokenInput, chatIdInput;
    private TextView statusText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        botTokenInput = findViewById(R.id.botToken);
        chatIdInput   = findViewById(R.id.chatId);
        statusText    = findViewById(R.id.statusText);
        Button saveBtn       = findViewById(R.id.saveBtn);
        Button permissionBtn = findViewById(R.id.permissionBtn);
        Button testBtn       = findViewById(R.id.testBtn);

        botTokenInput.setText(prefs.getString("bot_token", ""));
        chatIdInput.setText(prefs.getString("chat_id", ""));

        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String token = botTokenInput.getText().toString().trim();
                String chat  = chatIdInput.getText().toString().trim();
                if (TextUtils.isEmpty(token) || TextUtils.isEmpty(chat)) {
                    Toast.makeText(MainActivity.this, "Fill both fields", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit().putString("bot_token", token)
                            .putString("chat_id", chat).apply();
                Toast.makeText(MainActivity.this, "Saved", Toast.LENGTH_SHORT).show();
            }
        });

        permissionBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                        "Open Settings > Notification access manually",
                        Toast.LENGTH_LONG).show();
                }
            }
        });

        testBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sendTest();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean enabled = isNotificationAccessEnabled();
        statusText.setText(enabled
            ? "Status: Notification access GRANTED"
            : "Status: Grant notification access below");
    }

    private boolean isNotificationAccessEnabled() {
        try {
            String flat = Settings.Secure.getString(getContentResolver(),
                    "enabled_notification_listeners");
            return flat != null && flat.contains(getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private void sendTest() {
        final String token = prefs.getString("bot_token", "");
        final String chat  = prefs.getString("chat_id", "");
        if (TextUtils.isEmpty(token) || TextUtils.isEmpty(chat)) {
            Toast.makeText(this, "Save settings first", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                final String result = telegramSend(token, chat, "Test from NotiForwarder");
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    static String telegramSend(String token, String chatId, String message) {
        try {
            String urlStr = "https://api.telegram.org/bot" + token + "/sendMessage";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String postData = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") +
                              "&text=" + URLEncoder.encode(message, "UTF-8");

            OutputStream os = conn.getOutputStream();
            os.write(postData.getBytes("UTF-8"));
            os.close();

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300)
                    ? conn.getInputStream() : conn.getErrorStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            conn.disconnect();

            return "HTTP " + code + ": " + sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
  }
