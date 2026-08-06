# 默认不启用混淆（build.gradle 中 release minifyEnabled=false），此处保留占位。

# JS 桥：网页通过 window.PanoAndroid 调用，方法名不可被混淆/移除
-keep class com.example.panoviewer.ui.AndroidBridge { *; }

# 收藏持久化用 LinkType.name()/valueOf() 序列化，枚举常量名须保留
-keep enum com.example.panoviewer.link.LinkType { *; }
