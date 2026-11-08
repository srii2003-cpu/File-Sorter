package com.narasa.imageorganiser.classifier

/** Exactly one per image; decides its album. */
enum class PrimaryCategory { DOCUMENT, SCREENSHOT, PHOTO, GRAPHIC, REVIEW }

/** Zero or more per image; power search, warnings and the junk queue. */
enum class TagName { SENSITIVE, RECEIPT, SCHOOLWORK, OFFICE, EVENT_INFO, QR, JUNK_CANDIDATE }

/** SUGGESTED -> CONFIRMED | REJECTED; confirmed junk -> TRASHED. REJECTED is never re-suggested. */
enum class TagState { SUGGESTED, CONFIRMED, REJECTED, TRASHED }

enum class TagKind { BUILTIN, USER }

enum class ImageStatus { PENDING, DONE, OCR_FAILED, UNREADABLE }

enum class Direction { SENT, RECEIVED }

enum class Source { WHATSAPP, TELEGRAM, DOWNLOADS, SCREENSHOTS, SHARED_IN }
