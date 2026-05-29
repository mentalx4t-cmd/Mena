package com.example.service.youtube

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YouTubeBroadcastListResponse(
    @Json(name = "items") val items: List<YouTubeBroadcastItem>
)

@JsonClass(generateAdapter = true)
data class YouTubeBroadcastItem(
    @Json(name = "id") val id: String,
    @Json(name = "snippet") val snippet: Snippet,
    @Json(name = "contentDetails") val contentDetails: ContentDetails?,
    @Json(name = "status") val status: BroadcastStatus?
)

@JsonClass(generateAdapter = true)
data class Snippet(
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String?,
    @Json(name = "publishedAt") val publishedAt: String?,
    @Json(name = "actualStartTime") val actualStartTime: String?
)

@JsonClass(generateAdapter = true)
data class ContentDetails(
    @Json(name = "boundStreamId") val boundStreamId: String?,
    @Json(name = "monitorStream") val monitorStream: MonitorStream?
)

@JsonClass(generateAdapter = true)
data class MonitorStream(
    @Json(name = "enableMonitorStream") val enableMonitorStream: Boolean?
)

@JsonClass(generateAdapter = true)
data class BroadcastStatus(
    @Json(name = "lifeCycleStatus") val lifeCycleStatus: String?
)

@JsonClass(generateAdapter = true)
data class YouTubeStreamListResponse(
    @Json(name = "items") val items: List<YouTubeStreamItem>
)

@JsonClass(generateAdapter = true)
data class YouTubeStreamItem(
    @Json(name = "id") val id: String,
    @Json(name = "cdn") val cdn: StreamCdn?
)

@JsonClass(generateAdapter = true)
data class StreamCdn(
    @Json(name = "ingestionType") val ingestionType: String?,
    @Json(name = "ingestionInfo") val ingestionInfo: IngestionInfo?
)

@JsonClass(generateAdapter = true)
data class IngestionInfo(
    @Json(name = "streamName") val streamName: String?,
    @Json(name = "ingestionAddress") val ingestionAddress: String?,
    @Json(name = "backupIngestionAddress") val backupIngestionAddress: String?
)
