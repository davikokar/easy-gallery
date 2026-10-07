package com.davide.seddio.easygallery.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class AlbumMembershipKind {
    ADDITION,
    EXCLUSION
}

class AlbumMembershipKindConverters {
    @TypeConverter
    fun fromKind(value: AlbumMembershipKind): String = value.name

    @TypeConverter
    fun toKind(value: String): AlbumMembershipKind = AlbumMembershipKind.valueOf(value)
}

@Entity(
    tableName = "albums",
    foreignKeys = [
        ForeignKey(
            entity = AlbumMembershipEntity::class,
            parentColumns = ["id"],
            childColumns = ["coverMembershipId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["coverMembershipId"])
    ]
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val rule: String? = null,
    val coverMembershipId: Long? = null
)

@Entity(
    tableName = "album_memberships",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["albumId", "mediaUri", "kind"], unique = true)
    ]
)
data class AlbumMembershipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val albumId: Long,
    val mediaUri: String,
    val displayName: String,
    val size: Long,
    val dateModified: Long,
    val kind: AlbumMembershipKind,
    val isOrphaned: Boolean
)
