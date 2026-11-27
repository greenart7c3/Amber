package com.greenart7c3.nostrsigner

object BuildFlavorChecker {
    private const val OFFLINE_ZAPSTORE_FLAVOR = "offlinezapstore"

    /** Both offline flavors: `offline` and `offlinezapstore`, the build behind the separate "Amber Offline" Zapstore listing. */
    @Suppress("SimplifyBooleanWithConstants")
    fun isOfflineFlavor(): Boolean {
        @Suppress("KotlinConstantConditions")
        return BuildConfig.FLAVOR == "offline" || BuildConfig.FLAVOR == OFFLINE_ZAPSTORE_FLAVOR
    }

    /**
     * Whether this is the F-Droid build (no self-update or Zapstore). A function
     * rather than the constant: the F-Droid release workflow flips
     * IS_FDROID_BUILD with sed, so code must never be simplified on its value.
     */
    fun isFdroidBuild(): Boolean = BuildConfig.IS_FDROID_BUILD
}
