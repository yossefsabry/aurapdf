package com.aurapdf.app.domain.usecase

import com.aurapdf.app.domain.model.PdfDocument
import com.aurapdf.app.domain.repository.PdfRepository
import javax.inject.Inject

/** Removes a PDF document from the library. */
class DeletePdfDocumentUseCase @Inject constructor(
    private val repository: PdfRepository
) {
    suspend operator fun invoke(document: PdfDocument) =
        repository.deleteDocument(document)
}
