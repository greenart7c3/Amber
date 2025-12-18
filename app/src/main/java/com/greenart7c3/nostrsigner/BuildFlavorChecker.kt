package com.greenart7c3.nostrsigner

object BuildFlavorChecker {
    @Suppress("SimplifyBooleanWithConstants")
    fun isOfflineFlavor(): Boolean {
        @Suppress("KotlinConstantConditions")
        return BuildConfig.FLAVOR == "offline"
    }
}
