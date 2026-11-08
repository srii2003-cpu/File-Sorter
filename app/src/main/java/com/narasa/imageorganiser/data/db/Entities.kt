package com.narasa.imageorganiser.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.narasa.imageorganiser.classifier.Direction
import com.narasa.imageorganiser.classifier.ImageStatus
import com.narasa.imageorganiser.classifier.PrimaryCategory
import com.narasa.imageorganiser.classifier.Source
import com.narasa.imageorganiser.classifier.TagKind
import com.narasa.imageorganiser.classifier.TagState

@Entity(
    tableName = "image",
    indices = [
        Index(value = ["media_id"], unique = true),
        Index(value = ["taken_date"]),
        Index(value = ["primary_category"]),
        Index(value = ["status"]),
    ],
)
data class ImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "media_id") val mediaId: Long,
    val source: Source,
    /** Null when unknown (see decision D4). */
    val direction: Direction?,
    @ColumnInfo(name = "taken_date") val takenDate: Long,
    val dhash: Long?,
    val width: Int,
    val height: Int,
    /** Null while the image is still pending classification. */
    @ColumnInfo(name = "primary_category") val primaryCategory: PrimaryCategory?,
    @ColumnInfo(name = "primary_reason") val primaryReason: String?,
    @ColumnInfo(name = "in_review") val inReview: Boolean = false,
    @ColumnInfo(name = "review_reason") val reviewReason: String?,
    val status: ImageStatus,
    @ColumnInfo(name = "model_version") val modelVersion: String?,
)

/** OCR text for search. rowid = image.id; FTS tables cannot hold foreign keys, so deletes are explicit. */
@Fts4
@Entity(tableName = "ocr_text")
data class OcrTextEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val imageId: Long,
    val text: String,
)

@Entity(tableName = "tag", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: TagKind,
)

@Entity(
    tableName = "image_tag",
    primaryKeys = ["image_id", "tag_id"],
    foreignKeys = [
        ForeignKey(entity = ImageEntity::class, parentColumns = ["id"], childColumns = ["image_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class ImageTagEntity(
    @ColumnInfo(name = "image_id") val imageId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
    val reason: String,
    val state: TagState,
    @ColumnInfo(name = "changed_at") val changedAt: Long,
)
