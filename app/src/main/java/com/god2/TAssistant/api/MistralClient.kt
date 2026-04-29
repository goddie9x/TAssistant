package com.god2.TAssistant.api
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
data class MistralRequest(
    val model: String = "mistral-small-latest",
    val messages: List<Message>,
    val response_format: Map<String, String> = mapOf("type" to "json_object")
)
data class Message(val role: String, val content: String)
data class AssistantAction(
    val action: String,
    val target: String?,
    val message: String
)
interface MistralApi {
    @POST("v1/chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") apiKey: String,
        @Body request: MistralRequest
    ): Response<okhttp3.ResponseBody>
}
object RetrofitClient {
    val instance: MistralApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.mistral.ai/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MistralApi::class.java)
    }
}
