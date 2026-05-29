package com.example.service.youtube

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface YouTubeApiService {
    @GET("youtube/v3/liveBroadcasts")
    suspend fun listLiveBroadcasts(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "id,snippet,contentDetails,status",
        @Query("broadcastStatus") broadcastStatus: String = "all",
        @Query("mine") mine: Boolean = true
    ): YouTubeBroadcastListResponse

    @GET("youtube/v3/liveStreams")
    suspend fun listLiveStreams(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "id,cdn,status",
        @Query("id") streamId: String
    ): YouTubeStreamListResponse
}
