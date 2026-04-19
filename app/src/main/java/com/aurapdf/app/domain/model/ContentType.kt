package com.aurapdf.app.domain.model

/** Classification of a text block's role in the PDF layout. */
sealed class ContentType {
    /** Main body text — should be read. */
    object Paragraph : ContentType()
    /** Section heading — read (briefly). */
    object Title : ContentType()
    /** Footnote / endnote — configurable. */
    object Footnote : ContentType()
    /** Running page header. */
    object Header : ContentType()
    /** Running page footer. */
    object Footer : ContentType()
    /** Page number (standalone digit or "N of M" pattern). */
    object PageNumber : ContentType()
    /** Caption below a figure or table. */
    object FigureCaption : ContentType()
    /** Table cell or header. */
    object Table : ContentType()
    /** Could not be classified with confidence. */
    object Unknown : ContentType()
}
