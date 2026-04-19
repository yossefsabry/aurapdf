package com.aurapdf.app.domain.usecase

import com.aurapdf.app.domain.repository.PdfRepository
import javax.inject.Inject

/**
 * Persists a new PDF document after the user picks it via SAF.
 * Returns the new document's database id.
 */
class AddPdfDocumentUseCase @Inject constructor(
    private val repository: PdfRepository
) {
    suspend operator fun invoke(uri: String, name: String): Long =
        repository.addDocument(uri, name)
}
