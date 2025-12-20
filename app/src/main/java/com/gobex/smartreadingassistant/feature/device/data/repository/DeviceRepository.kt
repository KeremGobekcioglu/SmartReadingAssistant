package com.gobex.smartreadingassistant.feature.device.data.repository

import com.gobex.smartreadingassistant.feature.device.data.Esp32ApiService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor() {
    private var apiService : Esp32ApiService? = null
    private var currentIP : String? = null

    // http engine which manages timeouts , connection pooling ...
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)  // fast fail if device is gone
        .readTimeout(20, TimeUnit.SECONDS) // wait for image at least some time
        .build()

    // This method builds a retrofit instance to talk the esp32 server.
    fun connectToDeviceServer(ipAddress : String)
    {
        if(ipAddress == currentIP && apiService != null) return
        val retrofit = Retrofit.Builder()
            .baseUrl("http://$ipAddress/")
            .client(client)
            .build()

        apiService = retrofit.create(Esp32ApiService::class.java)
        currentIP = ipAddress
    }

    suspend fun captureImage() : Result<ByteArray>
    {
        val service = apiService ?: return Result.failure(Exception("Exception: Cant connect to esp server."))
        return try {
            service.controlDevice("flash",1)
            val response = service.captureImage()
            service.controlDevice("flash",0)
            if( response.isSuccessful && response.body() != null ) Result.success(response.body()!!.bytes())
            else Result.failure(Exception("Capture failed : ${response.code()}"))
        }
        catch (e : Exception)
        {
            Result.failure(e)
        }
    }
}