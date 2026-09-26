package com.asinosoft.cdm

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.yandex.mobile.ads.common.YandexAds

class App: Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        YandexAds.initialize(this) {
            Log.i("app", "YandexAds initialized")
        }
    }
}
