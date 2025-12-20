package com.gobex.smartreadingassistant.feature.device.data

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface Esp32ApiService {

    @GET("/capture")
    suspend fun captureImage(): Response<ResponseBody>

    // Usage: ?var=flash&val=1
    @GET("/control")
    suspend fun controlDevice(
        @Query("var") variable: String,
        @Query("val") value: Int
    ) : Response<ResponseBody>
}