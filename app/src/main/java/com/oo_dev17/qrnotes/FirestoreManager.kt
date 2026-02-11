package com.oo_dev17.qrnotes

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

object FirestoreManager {

    /**
     * Returns a reference to the 'qrNotes' subcollection for the currently logged-in user.
     * Returns null if no user is signed in.
     */
    fun getUserNotesCollection(): CollectionReference? {
        val userId = Firebase.auth.currentUser?.uid
        return if (userId != null) {
            Firebase.firestore.collection("users").document(userId).collection("qrNotes")
        } else {
            null
        }
    }
}


