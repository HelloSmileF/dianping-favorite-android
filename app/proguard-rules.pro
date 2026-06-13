# 保留 WebView JS 接口
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保留 Gson 用的数据类
-keep class com.example.dianpinghelper.model.** { *; }
