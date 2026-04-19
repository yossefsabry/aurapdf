package com.aurapdf.app.domain.usecase

import com.aurapdf.app.domain.repository.PdfRepository
import javax.inject.Inject

/** Saves the current page and scroll position so the user can resume reading later. */
class SaveReadingPositionUseCase @Inject constructor(
    private val repository: PdfRepository
) {
    suspend operator fun invoke(id: Long, page: Int, scrollOffset: Int) =
        repository.savePosition(id, page, scrollOffset)
}
