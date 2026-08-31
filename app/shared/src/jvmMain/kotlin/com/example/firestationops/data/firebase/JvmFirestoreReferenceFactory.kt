package com.example.firestationops.data.firebase

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

internal object JvmFirestoreReferenceFactory {
    fun document(firestore: FirebaseFirestore, documentPath: String): DocumentReference {
        val parts = documentPath.split("/")
        require(parts.size % 2 == 0) { "Document path requires an even number of segments: $documentPath" }
        var collection: CollectionReference = firestore.collection(parts.first())
        var index = 1
        while (index < parts.size) {
            val document = collection.document(parts[index])
            index++
            if (index >= parts.size) {
                return document
            }
            collection = document.collection(parts[index])
            index++
        }
        error("Unable to resolve document path: $documentPath")
    }

    fun collection(firestore: FirebaseFirestore, collectionPath: String): CollectionReference {
        val parts = collectionPath.split("/")
        require(parts.size % 2 == 1) { "Collection path requires an odd number of segments: $collectionPath" }
        var collection: CollectionReference = firestore.collection(parts.first())
        var index = 1
        while (index < parts.size) {
            val document = collection.document(parts[index])
            index++
            if (index >= parts.size) {
                return document.collection(parts.last())
            }
            collection = document.collection(parts[index])
            index++
        }
        return collection
    }
}
