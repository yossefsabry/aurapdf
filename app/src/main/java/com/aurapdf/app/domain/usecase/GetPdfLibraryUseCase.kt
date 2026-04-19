package com.aurapdf.app.domain.usecase

import com.aurapdf.app.domain.model.PdfDocument
import com.aurapdf.app.domain.repository.PdfRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Returns a reactive stream of all PDF documents in the library. */
class GetPdfLibraryUseCase @Inject constructor(
    private val repository: PdfRepository
) {
    operator fun invoke(): Flow<List<PdfDocument>> = repository.getAllDocuments()
}
