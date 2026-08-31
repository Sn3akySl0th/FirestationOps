package com.example.firestationops.data.firebase

import android.net.Uri
import com.example.firestationops.data.sync.CloudDocument
import com.example.firestationops.data.sync.CloudSyncClient
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

class AndroidCloudSyncClient(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : CloudSyncClient {
    override suspend fun getDocument(documentPath: String): CloudDocument {
        val snapshot = FirestoreReferenceFactory.document(firestore, documentPath).get().await()
        return CloudDocument(
            id = snapshot.id,
            data = snapshot.data ?: emptyMap(),
            exists = snapshot.exists()
        )
    }

    override suspend fun listCollection(collectionPath: String): List<CloudDocument> {
        val snapshot = FirestoreReferenceFactory.collection(firestore, collectionPath).get().await()
        return snapshot.documents.map { document ->
            CloudDocument(
                id = document.id,
                data = document.data ?: emptyMap(),
                exists = document.exists()
            )
        }
    }

    override suspend fun setDocument(documentPath: String, data: Map<String, Any?>, merge: Boolean) {
        val reference = FirestoreReferenceFactory.document(firestore, documentPath)
        if (merge) {
            reference.set(data, SetOptions.merge()).await()
        } else {
            reference.set(data).await()
        }
    }

    override suspend fun deleteDocument(documentPath: String) {
        FirestoreReferenceFactory.document(firestore, documentPath).delete().await()
    }

    override suspend fun uploadStorageFile(storagePath: String, localFilePath: String): String {
        val file = File(localFilePath)
        return storage.reference.child(storagePath)
            .putFile(Uri.fromFile(file))
            .await()
            .storage
            .downloadUrl
            .await()
            .toString()
    }

    override suspend fun downloadStorageFile(storagePath: String, localFilePath: String) {
        val file = File(localFilePath)
        file.parentFile?.mkdirs()
        storage.reference.child(storagePath).getFile(file).await()
    }
}
