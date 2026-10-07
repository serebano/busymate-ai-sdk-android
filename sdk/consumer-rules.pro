# Preserve legacy JavascriptInterface handlers when the host enables shrinking.
-keepclassmembers class ai.busymate.whitelabel.BusymateAIWebViewBridge { @android.webkit.JavascriptInterface <methods>; }
-keepclassmembers class ai.busymate.bridge.** { @android.webkit.JavascriptInterface <methods>; }
