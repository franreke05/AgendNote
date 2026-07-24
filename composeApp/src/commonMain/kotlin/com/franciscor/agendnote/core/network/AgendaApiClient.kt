package com.franciscor.agendnote.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class AgendaApiClient(
    private val baseUrl: String,
    private val appSecret: String,
) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }

    private fun withAuth(builder: io.ktor.client.request.HttpRequestBuilder) {
        builder.header("x-app-secret", appSecret)
    }

    suspend fun fetchLabels(): List<LabelDto> {
        val response: LabelsResponse = client
            .get("$normalizedBaseUrl/api-labels") {
                withAuth(this)
            }
            .body()
        return response.labels
    }

    suspend fun createLabel(name: String, colorHex: String): LabelDto {
        val response: LabelResponse = client
            .post("$normalizedBaseUrl/api-labels") {
                withAuth(this)
                contentType(ContentType.Application.Json)
                setBody(CreateLabelRequest(name = name, color_hex = colorHex))
            }
            .body()
        return response.label
    }

    suspend fun fetchTasks(day: String): List<TaskDto> {
        val response: TasksResponse = client
            .get("$normalizedBaseUrl/api-tasks") {
                withAuth(this)
                parameter("day", day)
            }
            .body()
        return response.tasks
    }

    suspend fun createTask(request: CreateTaskRequest): TaskDto {
        val response: TaskResponse = client
            .post("$normalizedBaseUrl/api-tasks") {
                withAuth(this)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .body()
        return response.task
    }

    suspend fun updateTask(request: UpdateTaskRequest): TaskDto {
        val response: TaskResponse = client
            .patch("$normalizedBaseUrl/api-tasks") {
                withAuth(this)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .body()
        return response.task
    }

    suspend fun deleteTask(id: String): Boolean {
        val response: SuccessResponse = client
            .delete("$normalizedBaseUrl/api-tasks") {
                withAuth(this)
                parameter("id", id)
            }
            .body()
        return response.success
    }

    suspend fun deleteAllTasks(): Boolean {
        val response: SuccessResponse = client
            .delete("$normalizedBaseUrl/api-tasks") {
                withAuth(this)
                parameter("all", "true")
            }
            .body()
        return response.success
    }

    suspend fun fetchSetting(key: String): String? {
        val response: SettingResponse = client
            .get("$normalizedBaseUrl/api-settings") {
                withAuth(this)
                parameter("key", key)
            }
            .body()
        return response.setting?.value
    }

    suspend fun updateSetting(key: String, value: String): SettingDto {
        val response: SettingResponse = client
            .post("$normalizedBaseUrl/api-settings") {
                withAuth(this)
                contentType(ContentType.Application.Json)
                setBody(UpdateSettingRequest(key = key, value = value))
            }
            .body()
        return response.setting ?: SettingDto(key = key, value = value)
    }

    suspend fun deleteLabel(id: String): Boolean {
        val response: SuccessResponse = client
            .delete("$normalizedBaseUrl/api-labels") {
                withAuth(this)
                parameter("id", id)
            }
            .body()
        return response.success
    }

    suspend fun deleteAllLabels(): Boolean {
        val response: SuccessResponse = client
            .delete("$normalizedBaseUrl/api-labels") {
                withAuth(this)
                parameter("all", "true")
            }
            .body()
        return response.success
    }

    suspend fun fetchTaskSeries(): List<TaskSeriesDto> {
        val response: TaskSeriesListResponse = client
            .get("$normalizedBaseUrl/api-task-series") {
                withAuth(this)
            }
            .body()
        return response.series
    }

    suspend fun createTaskSeries(request: CreateTaskSeriesRequest): TaskSeriesDto {
        val response: TaskSeriesResponse = client
            .post("$normalizedBaseUrl/api-task-series") {
                withAuth(this)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .body()
        return response.series
    }

    suspend fun updateTaskSeries(request: UpdateTaskSeriesRequest): TaskSeriesDto {
        val response: TaskSeriesResponse = client
            .patch("$normalizedBaseUrl/api-task-series") {
                withAuth(this)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .body()
        return response.series
    }

    suspend fun deleteTaskSeries(id: String): Boolean {
        val response: SuccessResponse = client
            .delete("$normalizedBaseUrl/api-task-series") {
                withAuth(this)
                parameter("id", id)
            }
            .body()
        return response.success
    }
}
