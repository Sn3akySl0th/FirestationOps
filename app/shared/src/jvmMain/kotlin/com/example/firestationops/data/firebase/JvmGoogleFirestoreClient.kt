package com.example.firestationops.data.firebase

import com.example.firestationops.data.sync.CloudDocument
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Desktop Firestore access through the Google Java SDK.
 *
 * GitLive's generic [Map] encoding relies on kotlinx.serialization and fails for [Any] values.
 */
internal object JvmGoogleFirestoreClient {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun getDocument(documentPath: String): CloudDocument {
        val snapshot = JvmFirestoreReferenceFactory.document(firestore, documentPath).get().await()
        return CloudDocument(
            id = snapshot.id,
            data = snapshot.data ?: emptyMap(),
            exists = snapshot.exists()
        )
    }

    suspend fun listCollection(collectionPath: String): List<CloudDocument> {
        val snapshot = JvmFirestoreReferenceFactory.collection(firestore, collectionPath).get().await()
        return snapshot.documents.map { document ->
            CloudDocument(
                id = document.id,
                data = document.data ?: emptyMap(),
                exists = document.exists()
            )
        }
    }

    suspend fun setDocument(documentPath: String, data: Map<String, Any?>, merge: Boolean = false) {
        val reference = JvmFirestoreReferenceFactory.document(firestore, documentPath)
        if (merge) {
            reference.set(data, SetOptions.merge()).await()
        } else {
            reference.set(data).await()
        }
    }

    suspend fun deleteDocument(documentPath: String) {
        JvmFirestoreReferenceFactory.document(firestore, documentPath).delete().await()
    }
}
