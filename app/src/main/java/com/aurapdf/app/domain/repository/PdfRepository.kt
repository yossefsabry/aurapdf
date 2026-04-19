package com.aurapdf.app.domain.repository

import com.aurapdf.app.domain.model.PdfDocument
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for PDF document persistence.
 * The domain layer depends only on this interface, never on the data layer directly.
 */
interface PdfRepository {

    /** Observe all documents in the library, ordered by most recently added. */
    fun getAllDocuments(): Flow<List<PdfDocument>>

    /** Add a new document to the library (e.g., after SAF pick). */
    suspend fun addDocument(uri: String, name: String): Long

    /** Remove a document from the library and release its SAF permission. */
    suspend fun deleteDocument(document: PdfDocument)

    /** Persist the current reading position for a document. */
    suspend fun savePosition(id: Long, page: Int, scrollOffset: Int)

    /** Update the cached total page count after first open. */
    suspend fun updateTotalPages(id: Long, totalPages: Int)

    /** Retrieve a single document by its id (suspend, not reactive). */
    suspend fun getDocumentById(id: Long): PdfDocument?
}
