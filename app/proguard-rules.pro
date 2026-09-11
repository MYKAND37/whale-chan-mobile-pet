# Keep WebView JS interfaces if any are added later.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
