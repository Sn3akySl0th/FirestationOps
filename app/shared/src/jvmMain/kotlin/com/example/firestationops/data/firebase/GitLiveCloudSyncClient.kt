package com.example.firestationops.data.firebase

import android.net.Uri
import com.example.firestationops.data.sync.CloudDocument
import com.example.firestationops.data.sync.CloudSyncClient
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

class GitLiveCloudSyncClient : CloudSyncClient {
    private val storage = FirebaseStorage.getInstance()

    override suspend fun getDocument(documentPath: String): CloudDocument =
        JvmGoogleFirestoreClient.getDocument(documentPath)

    override suspend fun listCollection(collectionPath: String): List<CloudDocument> =
        JvmGoogleFirestoreClient.listCollection(collectionPath)

    override suspend fun setDocument(documentPath: String, data: Map<String, Any?>, merge: Boolean) {
        JvmGoogleFirestoreClient.setDocument(documentPath, data, merge)
    }

    override suspend fun deleteDocument(documentPath: String) {
        JvmGoogleFirestoreClient.deleteDocument(documentPath)
    }

    override suspend fun uploadStorageFile(storagePath: String, localFilePath: String): String {
        val reference = storage.reference.child(storagePath)
        reference.putFile(Uri.fromFile(File(localFilePath))).await()
        return reference.downloadUrl.await().toString()
    }

    override suspend fun downloadStorageFile(storagePath: String, localFilePath: String) {
        val file = File(localFilePath)
        file.parentFile?.mkdirs()
        storage.reference.child(storagePath).getFile(file).await()
    }
}
