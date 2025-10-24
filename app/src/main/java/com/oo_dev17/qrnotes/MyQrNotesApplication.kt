package com.oo_dev17.qrnotes;

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

// File: app/src/main/java/com/oo_dev17/qrnotes/MyQrNotesApplication.kt
public final class MyQrNotesApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase first
        Firebase.initialize(this)

        // Get the FirebaseAppCheck instance
        val firebaseAppCheck = FirebaseAppCheck.getInstance()

        // Initialize App Check with the Play Integrity provider
        // This is the line that "installs" the provider
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}