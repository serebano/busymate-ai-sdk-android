package ai.busymate.view

import ai.busymate.Busymate
import ai.busymate.BusymateUnread
import ai.busymate.OnUnreadListener
import ai.busymate.sdk.R
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * The chat button for your main screen: a Material 3 floating action button with a count badge that
 * follows [Busymate.unread] by itself.
 *
 *     <ai.busymate.view.BusymateLauncherButton
 *         android:id="@+id/chat" android:layout_width="wrap_content" android:layout_height="wrap_content" />
 *
 *     chat.setOnClickListener { startActivity(Intent(this, ChatActivity::class.java)) }
 *
 * A tap tells the chat to open at the new message ([Busymate.openChat]) and then runs your click
 * listener. Colours come from your Material 3 theme ([BusymateColors.from]); the badge text scales
 * with the system font size; TalkBack reads "Chat, 2 unread messages".
 */
class BusymateLauncherButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val fab = ImageView(context)
    private val badge = TextView(context)
    private val listener = OnUnreadListener { if (sample == null) show(it) }
    private var shown: BusymateUnread = BusymateUnread.NONE

    /** Replace to use your own colours. */
    var colors: BusymateColors = BusymateColors.from(context)
        set(value) { field = value; paint() }

    init {
        // Exactly the button's 56dp, like the Compose component, so both sit on the same insets. The badge
        // and the shadow draw a little outside; see onAttachedToWindow.
        clipToPadding = false
        clipChildren = false
        isClickable = true
        isFocusable = true

        fab.setImageResource(R.drawable.busymate_ic_chat)
        fab.scaleType = ImageView.ScaleType.CENTER
        fab.elevation = dp(6f).toFloat()
        fab.outlineProvider = ViewOutlineProvider.BACKGROUND
        fab.isDuplicateParentStateEnabled = true
        fab.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(fab, LayoutParams(dp(56f), dp(56f), Gravity.CENTER))

        badge.gravity = Gravity.CENTER
        badge.includeFontPadding = false
        badge.maxLines = 1
        badge.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        badge.minWidth = dp(16f)
        badge.minHeight = dp(16f)
        badge.setPadding(dp(4f), 0, dp(4f), 0)
        // Above the button in z-order, without a shadow of its own.
        badge.elevation = dp(7f).toFloat()
        badge.outlineProvider = null
        badge.visibility = View.GONE
        badge.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))

        paint()
        show(if (isInEditMode) BusymateUnread(2, null, null) else BusymateUnread.NONE, animate = false)
    }

    /**
     * A fixed value to show instead of following [Busymate.unread] (layout previews, screenshots,
     * tests). Null, the default, follows the SDK.
     */
    var sample: BusymateUnread? = null
        set(value) {
            if (field == value) return
            field = value
            show(value ?: Busymate.unread.value)
        }

    /** Your own icon (24dp); it is tinted with the container's content colour. */
    fun setIcon(icon: Drawable?) {
        fab.setImageDrawable(icon ?: ContextCompat.getDrawable(context, R.drawable.busymate_ic_chat))
    }

    override fun performClick(): Boolean {
        Busymate.openChat()
        return super.performClick()
    }

    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The badge overhangs the corner, so the parent must not clip it (what Material's BadgeUtils does too).
        (parent as? ViewGroup)?.let { it.clipChildren = false; it.clipToPadding = false }
        if (!isInEditMode) Busymate.addOnUnreadListener(listener)
    }

    override fun onDetachedFromWindow() {
        Busymate.removeOnUnreadListener(listener)
        super.onDetachedFromWindow()
    }

    private fun paint() {
        val shape = GradientDrawable().apply { cornerRadius = dp(16f).toFloat(); setColor(this@BusymateLauncherButton.colors.container) }
        val pressed = ColorStateList.valueOf((colors.onContainer and 0x00FFFFFF) or 0x1F000000)
        fab.background = RippleDrawable(pressed, shape, null)
        fab.imageTintList = ColorStateList.valueOf(colors.onContainer)
        badge.background = GradientDrawable().apply { cornerRadius = dp(100f).toFloat(); setColor(this@BusymateLauncherButton.colors.badge) }
        badge.setTextColor(colors.onBadge)
    }

    private fun show(unread: BusymateUnread, animate: Boolean = true) {
        val had = shown.hasUnread
        shown = unread
        contentDescription = if (unread.hasUnread) {
            resources.getQuantityString(R.plurals.busymate_launcher_unread, unread.count, unread.count)
        } else resources.getString(R.string.busymate_launcher_label)
        badge.text = unread.badgeText
        badge.animate().cancel()
        if (!unread.hasUnread) {
            badge.visibility = View.GONE
            return
        }
        // Sits on the button's top end corner, a little outside it.
        badge.translationY = -dp(4f).toFloat()
        badge.translationX = (if (layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1) * dp(4f).toFloat()
        badge.visibility = View.VISIBLE
        if (!had && animate && motionAllowed()) {
            badge.scaleX = 0f; badge.scaleY = 0f
            badge.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator(2f)).start()
        } else {
            badge.scaleX = 1f; badge.scaleY = 1f
        }
    }

    private fun motionAllowed() = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()

    private fun dp(value: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}
