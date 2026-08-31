package com.example.firestationops.data.sync

data class CloudDocument(
    val id: String,
    val data: Map<String, Any?>,
    val exists: Boolean
)

interface CloudSyncClient {
    suspend fun getDocument(documentPath: String): CloudDocument
    suspend fun listCollection(collectionPath: String): List<CloudDocument>
    suspend fun setDocument(documentPath: String, data: Map<String, Any?>, merge: Boolean = true)
    suspend fun deleteDocument(documentPath: String)
    suspend fun uploadStorageFile(
        storagePath: String,
        localFilePath: String,
        onProgress: ((Int) -> Unit)? = null
    ): String
    suspend fun downloadStorageFile(storagePath: String, localFilePath: String)
}

interface SyncAttachmentCache {
    fun attachmentFilePath(attachmentId: String): String
    fun fileExists(path: String): Boolean
    fun copyToAttachmentPath(attachmentId: String, sourcePath: String): String
}
