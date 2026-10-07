package com.davide.seddio.easygallery.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Insert
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Insert
    suspend fun insertMembership(membership: AlbumMembershipEntity): Long

    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    @Update
    suspend fun updateMembership(membership: AlbumMembershipEntity)

    @Delete
    suspend fun deleteAlbum(album: AlbumEntity)

    @Delete
    suspend fun deleteMembership(membership: AlbumMembershipEntity)

    @Query("DELETE FROM album_memberships WHERE id = :membershipId")
    suspend fun deleteMembershipById(membershipId: Long)

    @Query("SELECT * FROM albums ORDER BY createdAt ASC, id ASC")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM album_memberships ORDER BY id ASC")
    fun observeAllMemberships(): Flow<List<AlbumMembershipEntity>>

    @Query("SELECT * FROM album_memberships WHERE albumId = :albumId ORDER BY id ASC")
    fun observeMembershipsForAlbum(albumId: Long): Flow<List<AlbumMembershipEntity>>

    @Query("SELECT * FROM albums WHERE id = :albumId LIMIT 1")
    suspend fun getAlbumById(albumId: Long): AlbumEntity?

    @Query("SELECT * FROM album_memberships WHERE id = :membershipId LIMIT 1")
    suspend fun getMembershipById(membershipId: Long): AlbumMembershipEntity?

    @Query(
        """
        UPDATE album_memberships
        SET mediaUri = :toMediaUri
        WHERE id = :membershipId
          AND mediaUri = :fromMediaUri
          AND mediaUri != :toMediaUri
          AND NOT EXISTS (
              SELECT 1
              FROM album_memberships AS existing
              WHERE existing.albumId = album_memberships.albumId
                AND existing.kind = album_memberships.kind
                AND existing.mediaUri = :toMediaUri
                AND existing.id != :membershipId
          )
        """
    )
    suspend fun updateMembershipUriIfNoConflict(
        membershipId: Long,
        fromMediaUri: String,
        toMediaUri: String
    ): Int
}
