package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.webview.WebViewActivity

class ReaderErrorView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : LinearLayout(context, attrs) {
        lateinit var binding: ReaderErrorBinding
            private set

        var viewer: PagerViewer? = null
            set(value) {
                field = value
                binding.actionRetry.viewer = viewer
                binding.actionOpenInWebView.viewer = viewer
            }

        override fun onFinishInflate() {
            super.onFinishInflate()
            binding = ReaderErrorBinding.bind(this)
        }

        fun configureView(url: String?): ReaderErrorBinding {
            if (url?.startsWith("http", true) == true) {
                binding.actionOpenInWebView.isVisible = true
                binding.actionOpenInWebView.setOnClickListener {
                    context.startActivity(WebViewActivity.newIntent(context, url))
                }
            } else {
                binding.actionOpenInWebView.isVisible = false
            }
            binding.root.isVisible = true
            return binding
        }
    }
