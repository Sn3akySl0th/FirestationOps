package com.example.firestationops.data.firebase

import android.net.Uri
import com.example.firestationops.data.sync.CloudDocument
import com.example.firestationops.data.sync.CloudSyncClient
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    override suspend fun uploadStorageFile(
        storagePath: String,
        localFilePath: String,
        onProgress: ((Int) -> Unit)?
    ): String {
        val reference = storage.reference.child(storagePath)
        val uploadTask = reference.putFile(Uri.fromFile(File(localFilePath)))

        if (onProgress == null) {
            uploadTask.await()
            return reference.downloadUrl.await().toString()
        }

        return suspendCancellableCoroutine { continuation ->
            uploadTask.addOnProgressListener { snapshot ->
                if (snapshot.totalByteCount > 0L) {
                    val percent = ((100.0 * snapshot.bytesTransferred) / snapshot.totalByteCount).toInt()
                    onProgress(percent.coerceIn(0, 99))
                }
            }
            uploadTask.addOnSuccessListener {
                reference.downloadUrl
                    .addOnSuccessListener { uri ->
                        onProgress(100)
                        continuation.resume(uri.toString())
                    }
                    .addOnFailureListener { error -> continuation.resumeWithException(error) }
            }.addOnFailureListener { error -> continuation.resumeWithException(error) }
            continuation.invokeOnCancellation { uploadTask.cancel() }
        }
    }

    override suspend fun downloadStorageFile(storagePath: String, localFilePath: String) {
        val file = File(localFilePath)
        file.parentFile?.mkdirs()
        storage.reference.child(storagePath).getFile(file).await()
    }
}
