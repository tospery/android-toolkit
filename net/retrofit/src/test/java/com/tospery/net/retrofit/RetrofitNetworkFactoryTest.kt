package com.tospery.net.retrofit

import com.squareup.moshi.JsonClass
import com.tospery.base.logging.LogEntry
import com.tospery.base.logging.LogProvider
import com.tospery.base.logging.LogRegistry
import com.tospery.base.logging.NoOpLogProvider
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertTrue
import retrofit2.http.Body
import retrofit2.http.POST
import org.junit.After

class RetrofitNetworkFactoryTest {

    @After
    fun resetLogProvider() {
        LogRegistry.install(NoOpLogProvider)
    }

    private interface SampleService {
        @retrofit2.http.GET("repos")
        suspend fun repos(): String
    }

    private interface SampleBodyService {
        @POST("login")
        fun login(
            @Body body: SampleBody,
        ): Call<ResponseBody>
    }

    @JsonClass(generateAdapter = false)
    private data class SampleBody(
        val token: String,
    )

    @Test
    fun factoryCreatesOkHttpClientWithConfiguredTimeouts() {
        val config = RetrofitNetworkConfig(
            baseUrl = "https://api.github.com/",
            connectTimeoutMillis = 1_000L,
            readTimeoutMillis = 2_000L,
            writeTimeoutMillis = 3_000L,
        )

        val client = RetrofitNetworkFactory.createOkHttpClient(config)

        assertEquals(1_000, client.connectTimeoutMillis)
        assertEquals(2_000, client.readTimeoutMillis)
        assertEquals(3_000, client.writeTimeoutMillis)
    }

    @Test
    fun factoryOkHttpClientAddsConfiguredDefaultHeaders() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = "ok"))
            server.start()

            val config = RetrofitNetworkConfig(
                baseUrl = server.url("/").toString(),
                defaultHeaders = mapOf(
                    "Accept" to "application/vnd.github+json",
                    "X-GitHub-Api-Version" to "2026-03-10",
                ),
            )
            val client = RetrofitNetworkFactory.createOkHttpClient(config)

            client.newCall(
                Request.Builder()
                    .url(server.url("/repos"))
                    .build(),
            ).execute().close()

            val request = server.takeRequest()
            assertEquals("application/vnd.github+json", request.headers["Accept"])
            assertEquals("2026-03-10", request.headers["X-GitHub-Api-Version"])
        }
    }

    @Test
    fun factoryOkHttpClientDoesNotOverrideExplicitHeader() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = "ok"))
            server.start()

            val config = RetrofitNetworkConfig(
                baseUrl = server.url("/").toString(),
                defaultHeaders = mapOf("Accept" to "application/vnd.github+json"),
            )
            val client = RetrofitNetworkFactory.createOkHttpClient(config)

            client.newCall(
                Request.Builder()
                    .url(server.url("/repos"))
                    .header("Accept", "application/vnd.github.raw+json")
                    .build(),
            ).execute().close()

            assertEquals("application/vnd.github.raw+json", server.takeRequest().headers["Accept"])
        }
    }

    @Test
    fun factoryOkHttpClientLogsNetworkRequestWithoutQuery() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = "ok"))
            server.start()

            val logger = RecordingLogProvider()
            LogRegistry.install(logger)
            val config = RetrofitNetworkConfig(
                baseUrl = server.url("/").toString(),
            )
            val client = RetrofitNetworkFactory.createOkHttpClient(
                config = config,
            )

            client.newCall(
                Request.Builder()
                    .url(server.url("/repos?access_token=secret"))
                    .build(),
            ).execute().close()

            val messages = logger.entries.map { it.message }
            assertEquals(3, messages.size)
            assertEquals("[GET]${server.url("/repos")}", messages[0])
            assertEquals("[200]${server.url("/repos")}", messages[1])
            assertEquals("ok", messages[2])
            assertTrue(messages.contains("<空>").not())
            assertTrue(messages.joinToString("\n").contains("secret").not())
            assertTrue(logger.entries.all { it.tag == NET_LOG_TAG })
        }
    }

    @Test
    fun factoryOkHttpClientLogsRequestAndResponseDetailsWithRedaction() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse(
                    code = 200,
                    body = """{"access_token":"server-secret","user":{"login":"tospery"}}""",
                    headers = okhttp3.Headers.headersOf("Content-Type", "application/json"),
                )
            )
            server.start()

            val logger = RecordingLogProvider()
            LogRegistry.install(logger)
            val config = RetrofitNetworkConfig(
                baseUrl = server.url("/").toString(),
            )
            val client = RetrofitNetworkFactory.createOkHttpClient(
                config = config,
            )

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/github/login"))
                    .header("Authorization", "Bearer client-secret")
                    .post(
                        """{"githubAccessToken":"client-secret","client":{"platform":"android"}}"""
                            .toRequestBody("application/json".toMediaType())
                    )
                    .build(),
            ).execute().close()

            val messages = logger.entries.map { it.message }
            assertEquals("[POST]${server.url("/v1/github/login")}", messages[0])
            assertTrue(messages[1].contains(""""githubAccessToken":"***""""))
            assertTrue(messages[1].contains(""""client":{"platform":"android"}"""))
            assertEquals("[200]${server.url("/v1/github/login")}", messages[2])
            assertTrue(messages[3].contains(""""access_token":"***""""))
            assertTrue(messages[3].contains(""""user":{"login":"tospery"}"""))
            assertTrue(messages.joinToString("\n").contains("client-secret").not())
            assertTrue(messages.joinToString("\n").contains("server-secret").not())
        }
    }

    @Test
    fun factoryOkHttpClientCanDisableSensitiveDataRedactionForDebugBuilds() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse(
                    code = 200,
                    body = """{"access_token":"server-secret"}""",
                    headers = okhttp3.Headers.headersOf("Content-Type", "application/json"),
                )
            )
            server.start()

            val logger = RecordingLogProvider()
            LogRegistry.install(logger)
            val config = RetrofitNetworkConfig(
                baseUrl = server.url("/").toString(),
            )
            val client = RetrofitNetworkFactory.createOkHttpClient(
                config = config,
                redactSensitiveData = false,
            )

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/github/login?access_token=query-secret"))
                    .post(
                        """{"githubAccessToken":"client-secret","client":{"platform":"android"}}"""
                            .toRequestBody("application/json".toMediaType())
                    )
                    .build(),
            ).execute().close()

            val messages = logger.entries.map { it.message }
            assertEquals(
                "[POST]${server.url("/v1/github/login?access_token=query-secret")}",
                messages[0],
            )
            assertTrue(messages[1].contains(""""githubAccessToken":"client-secret""""))
            assertEquals(
                "[200]${server.url("/v1/github/login?access_token=query-secret")}",
                messages[2],
            )
            assertTrue(messages[3].contains(""""access_token":"server-secret""""))
        }
    }

    @Test
    fun factoryCreatesRetrofitWithConfiguredBaseUrl() {
        val config = RetrofitNetworkConfig(
            baseUrl = "https://api.github.com/",
        )

        val retrofit = RetrofitNetworkFactory.createRetrofit(config)

        assertEquals("https://api.github.com/", retrofit.baseUrl().toString())
    }

    @Test
    fun factoryCreatesRetrofitService() {
        val config = RetrofitNetworkConfig(
            baseUrl = "https://api.github.com/",
        )
        val retrofit = RetrofitNetworkFactory.createRetrofit(config)

        val service = retrofit.create(SampleService::class.java)

        assertTrue(service::class.java.name.isNotBlank())
    }

    @Test
    fun factoryCreatesBodyConverterForKotlinDataClass() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = "ok"))
            server.start()

            val config = RetrofitNetworkConfig(
                baseUrl = server.url("/").toString(),
            )
            val retrofit = RetrofitNetworkFactory.createRetrofit(config)
            val service = retrofit.create(SampleBodyService::class.java)

            service.login(SampleBody(token = "secret")).execute().body()?.close()

            assertEquals("""{"token":"secret"}""", server.takeRequest().body?.utf8())
        }
    }

    private class RecordingLogProvider : LogProvider {
        val entries = mutableListOf<LogEntry>()

        override fun log(entry: LogEntry) {
            entries += entry
        }
    }

    private fun LogEntry.messageWithAttributes(): String {
        return message + attributes.joinToString(
            prefix = " {",
            postfix = "}",
        ) { "${it.key}=${it.value}" }
    }
}
