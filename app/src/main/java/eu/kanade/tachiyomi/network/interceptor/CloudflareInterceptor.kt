package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.WebViewClientCompat
import eu.kanade.tachiyomi.util.system.isOutdated
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch

class CloudflareInterceptor(private val context: Context) : WebViewInterceptor(context) {

    private val executor = ContextCompat.getMainExecutor(context)
    private val preferences: PreferencesHelper by injectLazy()
    private val networkHelper: NetworkHelper by injectLazy()

    override fun shouldIntercept(response: Response): Boolean {
        // Check if Cloudflare anti-bot is on
        return response.code in ERROR_CODES && response.header("Server") in SERVER_CHECK
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        try {
            response.close()
            networkHelper.cookieManager.remove(request.url, COOKIE_NAMES, 0)
            val oldCookie = networkHelper.cookieManager.get(request.url)
                .firstOrNull { it.name == "cf_clearance" }

            if (preferences.forceBypassCloudflare()) {
                for (i in 1..10) {
                    if (preferences.forceBypassCloudflare()) {
                        try {
                            resolveWithWebView(request, oldCookie)
                            break
                        } catch (e: CloudflareBypassException) {
                            // clearwebviewdata
                            context.applicationInfo?.dataDir?.let { File("$it/app_webview/").deleteRecursively() }
                            val msg = "Trying to force bypass cloudflare. Attempt: $i"
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                            if (i == 10) {
                                throw e
                            }
                        }
                    }
                }
            } else {
                resolveWithWebView(originalRequest, oldCookie)
            }

            return chain.proceed(request)
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareBypassException) {
            throw IOException(context.getString(R.string.failed_to_bypass_cloudflare))
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, oldCookie: Cookie?) {
        // We need to lock this thread until the WebView finds the challenge solution url, because
        // OkHttp doesn't support asynchronous interceptors.
        val latch = CountDownLatch(1)

        var webView: WebView? = null

        var challengeFound = false
        var cloudflareBypassed = false
        var isWebViewOutdated = false

        val origRequestUrl = originalRequest.url.toString()
        val headers =
            parseHeaders(originalRequest.headers)

        executor.execute {
            webView = createWebView(originalRequest)

            webView?.webViewClient = object : WebViewClientCompat() {
                override fun onPageFinished(view: WebView, url: String) {
                    fun isCloudFlareBypassed(): Boolean {
                        return networkHelper.cookieManager.get(origRequestUrl.toHttpUrl())
                            .firstOrNull { it.name == "cf_clearance" }
                            .let { it != null && it != oldCookie }
                    }

                    if (isCloudFlareBypassed()) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }

                    if (url == origRequestUrl && !challengeFound) {
                        // The first request didn't return the challenge, abort.
                        latch.countDown()
                    }
                }

                override fun onReceivedErrorCompat(
                    view: WebView,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String,
                    isMainFrame: Boolean,
                ) {
                    if (isMainFrame) {
                        if (errorCode in ERROR_CODES) {
                            // Found the Cloudflare challenge page.
                            challengeFound = true
                        } else {
                            // Unlock thread, the challenge wasn't found.
                            latch.countDown()
                        }
                    }
                }
            }

            webView?.loadUrl(origRequestUrl, headers)
        }

        latch.awaitFor30Seconds()

        executor.execute {
            if (!cloudflareBypassed) {
                isWebViewOutdated = webView?.isOutdated() == true
            }

            webView?.run {
                stopLoading()
                destroy()
            }
        }

        // Throw exception if we failed to bypass Cloudflare
        if (!cloudflareBypassed) {
            // Prompt user to update WebView if it seems too outdated
            if (isWebViewOutdated) {
                context.toast(R.string.please_update_webview, Toast.LENGTH_LONG)
            }

            throw CloudflareBypassException()
        }
    }
}

private val ERROR_CODES = listOf(403, 503)
private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")

private class CloudflareBypassException : Exception()
