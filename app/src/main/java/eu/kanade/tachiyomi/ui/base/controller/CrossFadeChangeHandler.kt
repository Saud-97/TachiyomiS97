package eu.kanade.tachiyomi.ui.base.controller

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.changehandler.AnimatorChangeHandler
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlin.math.roundToLong

class CrossFadeChangeHandler : AnimatorChangeHandler {
    constructor() : super()
    constructor(removesFromViewOnPush: Boolean) : super(removesFromViewOnPush)
    constructor(duration: Long) : super(duration)
    constructor(duration: Long, removesFromViewOnPush: Boolean) : super(
        duration,
        removesFromViewOnPush,
    )

    override fun getAnimator(
        container: ViewGroup,
        from: View?,
        to: View?,
        isPush: Boolean,
        toAddedToContainer: Boolean,
    ): Animator {
        val animatorSet = AnimatorSet()
        if (to != null) {
            val start = if (toAddedToContainer) 0F else to.alpha
            animatorSet.play(ObjectAnimator.ofFloat(to, View.ALPHA, start, 1f))
        }
        if (from != null) {
            animatorSet.play(ObjectAnimator.ofFloat(from, View.ALPHA, 0f))
        }
        if (isPush) {
            if (from != null) {
                animatorSet.play(
                    ObjectAnimator.ofFloat(
                        from,
                        View.TRANSLATION_X,
                        -from.width.toFloat() * 0.2f,
                    ),
                )
            }
            if (to != null) {
                animatorSet.play(
                    ObjectAnimator.ofFloat(
                        to,
                        View.TRANSLATION_X,
                        to.width.toFloat() * 0.2f,
                        0f,
                    ),
                )
            }
        } else {
            if (from != null) {
                animatorSet.play(
                    ObjectAnimator.ofFloat(
                        from,
                        View.TRANSLATION_X,
                        from.width.toFloat() * 0.2f,
                    ),
                )
            }
            if (to != null) {
                // Allow this to have a nice transition when coming off an aborted push animation or
                // from back gesture
                val fromLeft = from?.translationX ?: 0f
                animatorSet.play(
                    ObjectAnimator.ofFloat(
                        to,
                        View.TRANSLATION_X,
                        fromLeft - to.width * 0.2f,
                        0f,
                    ),
                )
            }
        }
        animatorSet.duration =
            if (isPush) {
                200
            } else {
                from
                    ?.let {
                        val startX = from.width.toFloat() * 0.2f
                        ((startX - it.x) / startX) * 150f
                    }?.roundToLong() ?: 150
            }
        animatorSet.doOnCancel { to?.x = 0f }
        animatorSet.doOnEnd { to?.x = 0f }
        if (!isPush &&
            from?.x != null &&
            from.x != 0f &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            animatorSet.interpolator =
                if (MainActivity.backVelocity != 0f) {
                    DecelerateInterpolator(MainActivity.backVelocity)
                } else {
                    LinearOutSlowInInterpolator()
                }
        }
        return animatorSet
    }

    override fun resetFromView(from: View) {
        from.translationX = 0f
    }

    override fun copy(): ControllerChangeHandler = CrossFadeChangeHandler(animationDuration, removesFromViewOnPush)
}
