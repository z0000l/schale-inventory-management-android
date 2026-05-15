# Compose / Coroutines / Kotlin 各自的 :consumer-rules.pro 会被自动合入
# 这里只保留 R8 full mode 下确实需要的最小额外规则

# AndroidViewModel 通过反射查找 (Application) 构造器
-keepclassmembers class net.terryu16.schale.inventory.ui.InventoryViewModel {
    <init>(android.app.Application);
}

# kotlinx-coroutines 1.8 起自带 consumer rules，但 volatile 字段保留有时仍需手写
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
