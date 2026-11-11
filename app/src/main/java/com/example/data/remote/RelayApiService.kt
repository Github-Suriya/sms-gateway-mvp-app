package com.example.data.remote

import com.example.data.model.HeartbeatRequest
import com.example.data.model.HeartbeatResponse
import com.example.data.model.JobEventRequest
import com.example.data.model.JobEventResponse
import com.example.data.model.PairRequest
import com.example.data.model.PairResponse
import com.example.data.model.PollJobResponse
import com.example.data.model.ReconcileJobResponse
import com.example.data.model.StartJobRequest
import com.example.data.model.StartJobResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface RelayApiService {

    @POST("devices/pair")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun pairDevice(
        @Body request: PairRequest
    ): Response<PairResponse>

    @POST("device/heartbeat")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun sendHeartbeat(
        @Body request: HeartbeatRequest
    ): Response<HeartbeatResponse>

    @GET("device/jobs/next")
    @Headers("Accept: application/json")
    suspend fun pollNextJob(): Response<PollJobResponse>

    @POST("device/jobs/{job_id}/start")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun startJob(
        @Path("job_id") jobId: String,
        @Body request: StartJobRequest
    ): Response<StartJobResponse>

    @POST("device/jobs/{job_id}/events")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun reportEvent(
        @Path("job_id") jobId: String,
        @Body request: JobEventRequest
    ): Response<JobEventResponse>

    @GET("device/jobs/{job_id}")
    @Headers("Accept: application/json")
    suspend fun reconcileJob(
        @Path("job_id") jobId: String
    ): Response<ReconcileJobResponse>
}
