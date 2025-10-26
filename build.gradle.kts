// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}
val defaultMinSdkVersion by extra(26)
val defaultMinSdkVersion1 by extra(defaultMinSdkVersion)
