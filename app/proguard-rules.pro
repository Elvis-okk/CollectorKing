# WebView JavaScript Bridge - keep class names
-keepclassmembers class com.collectorking.app.** {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebView related classes
-keep class * extends android.webkit.WebViewClient { *; }
-keep class * extends android.webkit.WebChromeClient { *; }

# Don't warn about JS interop
-dontwarn android.webkit.JavascriptInterface