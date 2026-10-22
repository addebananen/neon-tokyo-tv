package com.lumera.app.data.remote

import com.lumera.app.data.model.introdb.IntroDbSegmentsResponse
import retrofit2.http.GET
import retrofit2.http.Url

interface IntroDbService {
    @GET
    suspend fun getSegments(@Url url: String): IntroDbSegmentsResponse
}
