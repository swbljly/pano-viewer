# 正式发布版已启用混淆（build.gradle 中 release minifyEnabled=true，shrinkResources=true），
# 故必须保留以下 keep 规则，否则 release 混淆会破坏 JS 桥与 LinkType 枚举。

# JS 桥：网页通过 window.PanoAndroid 调用，方法名不可被混淆/移除
-keep class com.example.panoviewer.ui.AndroidBridge { *; }

# 收藏持久化用 LinkType.name()/valueOf() 序列化，枚举常量名须保留
-keep enum com.example.panoviewer.link.LinkType { *; }
