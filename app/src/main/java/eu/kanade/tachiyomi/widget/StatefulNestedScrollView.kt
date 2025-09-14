package eu.kanade.tachiyomi.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView

@SuppressLint("ClickableViewAccessibility")
class StatefulNestedScrollView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : NestedScrollView(context, attrs) {
        private var scrollerTask: Runnable? = null
        private var initialPosition = 0
        var hasStopped = true
        private var newCheck: Long = 50
        private var mScrollStoppedListener: OnScrollStoppedListener? = null

        init {
            scrollerTask =
                Runnable {
                    val newPosition = scrollY
                    if (initialPosition - newPosition == 0) {
                        if (mScrollStoppedListener != null) {
                            hasStopped = true
                            mScrollStoppedListener?.onScrollStopped()
                        }
                    } else {
                        startScrollerTask()
                    }
                }
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    (v as StatefulNestedScrollView).startScrollerTask()
                }
                false
            }
        }

        override fun startNestedScroll(
            axes: Int,
            type: Int,
        ): Boolean {
            hasStopped = false
            return super.startNestedScroll(axes, type)
        }

        private fun startScrollerTask() {
            initialPosition = scrollY
            postDelayed(scrollerTask, newCheck)
        }

        fun setScrollStoppedListener(scrollStoppedListener: OnScrollStoppedListener?) {
            mScrollStoppedListener = scrollStoppedListener
        }

        fun interface OnScrollStoppedListener {
            fun onScrollStopped()
        }
    }
