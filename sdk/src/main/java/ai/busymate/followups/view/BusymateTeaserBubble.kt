package ai.busymate.view

import ai.busymate.Busymate
import ai.busymate.BusymateUnread
import ai.busymate.OnTeaserListener
import ai.busymate.sdk.R
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * The follow-up message bubble for your main screen: the first sentence of the mate's new message
 * with a dismiss control. Place it next to [BusymateLauncherButton]; it shows and hides by itself
 * (it manages its own visibility) following [Busymate.teaser].
 *
 *     bubble.setOnClickListener { startActivity(Intent(this, ChatActivity::class.java)) }
 *
 * A tap tells the chat to open at that message ([Busymate.openChat]) and then runs your click
 * listener. Dismissing hides the bubble and leaves the message unread, so the badge keeps its count.
 * TalkBack announces the message when it appears; the text scales with the system font size.
 */
class BusymateTeaserBubble @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val title = TextView(context)
    private val preview = TextView(context)
    private val dismiss = ImageView(context)
    private val listener = OnTeaserListener { if (sample == null) show(it) }
    private val maxWidth = dp(320f)

    /** Replace to use your own colours. */
    var colors: BusymateColors = BusymateColors.from(context)
        set(value) { field = value; paint() }

    init {
        orientation = HORIZONTAL
        isClickable = true
        isFocusable = true
        elevation = dp(3f).toFloat()
        outlineProvider = ViewOutlineProvider.BACKGROUND
        ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        ViewCompat.replaceAccessibilityAction(this, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            resources.getString(R.string.busymate_teaser_open), null)

        val text = LinearLayout(context).apply {
            orientation = VERTICAL
            setPaddingRelative(dp(16f), dp(12f), 0, dp(12f))
        }
        title.setText(R.string.busymate_teaser_title)
        title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        text.addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        preview.setLineSpacing(0f, 1.15f)
        preview.maxLines = 3
        preview.ellipsize = TextUtils.TruncateAt.END
        preview.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        text.addView(preview, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) })
        addView(text, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        dismiss.setImageResource(R.drawable.busymate_ic_close)
        dismiss.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val pad = dp(14f) // a 20dp icon in a 48dp touch target
        dismiss.setPadding(pad, pad, pad, pad)
        dismiss.contentDescription = resources.getString(R.string.busymate_teaser_dismiss)
        dismiss.isClickable = true
        dismiss.isFocusable = true
        dismiss.setOnClickListener { if (sample != null) show(null) else Busymate.dismissTeaser() }
        addView(dismiss, LayoutParams(dp(48f), dp(48f)).apply { gravity = Gravity.TOP })

        paint()
        if (isInEditMode) {
            preview.text = "Your order has shipped. Want the tracking link?"
        } else {
            visibility = View.GONE
        }
    }

    /**
     * A fixed message to show instead of following [Busymate.teaser] (layout previews, screenshots,
     * tests); its dismiss control then only hides this view. Null, the default, follows the SDK.
     */
    var sample: BusymateUnread? = null
        set(value) {
            if (field == value) return
            field = value
            show(value ?: Busymate.teaser.value)
        }

    override fun performClick(): Boolean {
        Busymate.openChat()
        return super.performClick()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)
        val capped = when (mode) {
            MeasureSpec.UNSPECIFIED -> MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
            else -> MeasureSpec.makeMeasureSpec(minOf(size, maxWidth), if (mode == MeasureSpec.EXACTLY && size <= maxWidth) MeasureSpec.EXACTLY else MeasureSpec.AT_MOST)
        }
        super.onMeasure(capped, heightMeasureSpec)
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        paint()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) Busymate.addOnTeaserListener(listener)
    }

    override fun onDetachedFromWindow() {
        Busymate.removeOnTeaserListener(listener)
        animate().cancel()
        super.onDetachedFromWindow()
    }

    private fun paint() {
        val large = dp(20f).toFloat()
        val small = dp(6f).toFloat()
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        // A message bubble: the corner that points at the chat button (bottom end) is tighter.
        val radii = floatArrayOf(large, large, large, large, if (rtl) large else small, if (rtl) large else small, if (rtl) small else large, if (rtl) small else large)
        val shape = GradientDrawable().apply { cornerRadii = radii; setColor(this@BusymateTeaserBubble.colors.surface) }
        val pressed = ColorStateList.valueOf((colors.onSurface and 0x00FFFFFF) or 0x1F000000)
        background = RippleDrawable(pressed, shape, null)
        title.setTextColor(colors.accent)
        preview.setTextColor(colors.onSurface)
        dismiss.imageTintList = ColorStateList.valueOf(colors.onSurfaceVariant)
        dismiss.background = RippleDrawable(pressed, null, ShapeDrawable(OvalShape()))
    }

    private fun show(teaser: BusymateUnread?) {
        animate().cancel()
        val moving = motionAllowed() && isLaidOut
        if (teaser == null) {
            if (visibility != View.VISIBLE) return
            if (!moving) { visibility = View.GONE; return }
            animate().alpha(0f).setDuration(150).withEndAction { visibility = View.GONE }.start()
            return
        }
        preview.text = teaser.preview
        val wasVisible = visibility == View.VISIBLE
        visibility = View.VISIBLE
        if (wasVisible || !motionAllowed()) {
            alpha = 1f; translationY = 0f
            return
        }
        alpha = 0f
        translationY = dp(12f).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start()
    }

    private fun motionAllowed() = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()

    private fun dp(value: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}
