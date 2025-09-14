package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkHelper(
    val context: Context,
) {
    private val preferences: PreferencesHelper by injectLazy()

    private val cacheDir = File(context.cacheDir, "network_cache")

    private val cacheSize = 5L * 1024 * 1024 // 5 MiB

    val cookieJar = AndroidCookieJar()

    private val userAgentInterceptor by lazy { UserAgentInterceptor(::defaultUserAgent) }
    private val cloudflareInterceptor by lazy {
        CloudflareInterceptor(context, cookieJar, ::defaultUserAgent)
    }

    private val baseClientBuilder: OkHttpClient.Builder
        get() {
            val builder =
                OkHttpClient
                    .Builder()
                    .cookieJar(cookieJar)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .callTimeout(2, TimeUnit.MINUTES)
                    .addInterceptor(BrotliInterceptor)
                    .addInterceptor(UncaughtExceptionInterceptor())
                    .addInterceptor(userAgentInterceptor)
                    .apply {
//                        if (BuildConfig.DEBUG) {
//                        /*
//                        Network debugging and monitoring
//                         */
//                            addInterceptor(
//                                ChuckerInterceptor
//                                    .Builder(context)
//                                    .collector(ChuckerCollector(context))
//                                    .maxContentLength(250000L)
//                                    .redactHeaders(emptySet())
//                                    .alwaysReadResponseBody(false)
//                                    .build(),
//                            )
//                        }

                        when (preferences.dohProvider()) {
                            PREF_DOH_CLOUDFLARE -> dohCloudflare()
                            PREF_DOH_GOOGLE -> dohGoogle()
                            PREF_DOH_ADGUARD -> dohAdGuard()
                            PREF_DOH_QUAD9 -> dohQuad9()
                        }
                    }

            return builder
        }

    val client by lazy { baseClientBuilder.cache(Cache(cacheDir, cacheSize)).build() }

    @Suppress("UNUSED")
    val cloudflareClient by lazy {
        client
            .newBuilder()
            .addInterceptor(cloudflareInterceptor)
            .build()
    }

    val defaultUserAgent
        get() =
            preferences
                .defaultUserAgent()
                .get()
                .replace("\n", " ")
                .trim()

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/118.0"
    }
}
