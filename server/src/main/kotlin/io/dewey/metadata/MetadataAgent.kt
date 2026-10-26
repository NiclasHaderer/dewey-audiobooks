package io.dewey.metadata

import io.dewey.metadata.responses.MetadataAuthor
import io.dewey.metadata.responses.MetadataBook
import io.dewey.metadata.responses.MetadataChapters
import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataRegion
import io.dewey.metadata.responses.MetadataSeries
import kotlinx.coroutines.flow.Flow

interface MetadataAgent {
    val name: String
    val supportedRegions: List<MetadataRegion>

    suspend fun getAuthorByID(
        providerId: String,
        authorId: String,
        region: MetadataRegion,
    ): MetadataAuthor?

    suspend fun getBookByID(
        providerId: String,
        bookId: String,
        region: MetadataRegion,
    ): MetadataBook?

    suspend fun getBookByAsin(
        asin: String,
        region: MetadataRegion,
    ): MetadataBook? = null

    suspend fun getBookChapters(
        providerId: String,
        bookId: String,
        region: MetadataRegion,
    ): MetadataChapters? = null

    suspend fun getSeriesByID(
        providerId: String,
        seriesId: String,
        region: MetadataRegion,
    ): MetadataSeries?

    fun getAuthorByName(
        authorName: String,
        region: MetadataRegion,
        language: MetadataLanguage? = null,
    ): Flow<MetadataAuthor>

    fun getBookByName(
        bookName: String,
        region: MetadataRegion,
        keywords: String? = null,
        authorName: String? = null,
        narrator: String? = null,
        language: MetadataLanguage? = null,
    ): Flow<MetadataBook>

    fun getSeriesByName(
        seriesName: String,
        region: MetadataRegion,
        authorName: String? = null,
        language: MetadataLanguage? = null,
    ): Flow<MetadataSeries>
}
