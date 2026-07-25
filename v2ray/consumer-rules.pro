-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String,int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
-keepattributes Signature
-keep class libv2ray.* { *; }
-keep class app.tunnel.v2ray.data.model.ServerConfig{ *;}
-keep class app.tunnel.v2ray.V2RayConfigManager{ *;}
-keep class app.tunnel.v2ray.data.model**{ *;}
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes AnnotationDefault,RuntimeVisibleAnnotations