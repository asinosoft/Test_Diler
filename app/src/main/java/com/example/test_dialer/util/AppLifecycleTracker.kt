package com.example.test_dialer.util

import android.app.Activity
import android.app.Application
import android.os.Bundle

object AppLifecycleTracker : Application.ActivityLifecycleCallbacks {

    private var resumedActivityCount = 0

    val isAppInForeground: Boolean
        get() = resumedActivityCount > 0

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivityCount++
    }

    override fun onActivityPaused(activity: Activity) {
        resumedActivityCount = (resumedActivityCount - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
