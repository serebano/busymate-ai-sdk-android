package ai.busymate.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

/**
 * The Material 3 colour roles the View components use.
 *
 * [from] reads them from your theme when it is a Material 3 theme, so the button and the bubble match
 * your app. Without one it uses the device's dynamic colours (Android 12+) or the Material 3 baseline
 * palette, light or dark with the system. Pass your own to `colors` on either component to override.
 */
class BusymateColors(
    /** Launcher container (`primaryContainer`). */
    @ColorInt val container: Int,
    /** Launcher icon (`onPrimaryContainer`). */
    @ColorInt val onContainer: Int,
    /** Count badge (`error`). */
    @ColorInt val badge: Int,
    /** Count badge text (`onError`). */
    @ColorInt val onBadge: Int,
    /** Teaser bubble (`surfaceContainerHigh`). */
    @ColorInt val surface: Int,
    /** Teaser text (`onSurface`). */
    @ColorInt val onSurface: Int,
    /** Teaser dismiss icon (`onSurfaceVariant`). */
    @ColorInt val onSurfaceVariant: Int,
    /** Teaser title (`primary`). */
    @ColorInt val accent: Int,
) {
    companion object {
        @JvmStatic
        fun from(context: Context): BusymateColors {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val base = if (Build.VERSION.SDK_INT >= 31) dynamic(context, night) else baseline(night)
            return BusymateColors(
                container = themed(context, "colorPrimaryContainer") ?: base.container,
                onContainer = themed(context, "colorOnPrimaryContainer") ?: base.onContainer,
                badge = themed(context, "colorError")?.takeIf { themed(context, "colorOnError") != null } ?: base.badge,
                onBadge = themed(context, "colorOnError") ?: base.onBadge,
                surface = themed(context, "colorSurfaceContainerHigh") ?: base.surface,
                onSurface = themed(context, "colorOnSurface")?.takeIf { themed(context, "colorSurfaceContainerHigh") != null } ?: base.onSurface,
                onSurfaceVariant = themed(context, "colorOnSurfaceVariant")?.takeIf { themed(context, "colorSurfaceContainerHigh") != null } ?: base.onSurfaceVariant,
                accent = themed(context, "colorPrimary")?.takeIf { themed(context, "colorPrimaryContainer") != null } ?: base.accent,
            )
        }

        /** The Material 3 baseline scheme. */
        @JvmStatic
        fun baseline(night: Boolean): BusymateColors = if (night) BusymateColors(
            container = 0xFF4F378B.toInt(), onContainer = 0xFFEADDFF.toInt(),
            badge = 0xFFF2B8B5.toInt(), onBadge = 0xFF601410.toInt(),
            surface = 0xFF2B2930.toInt(), onSurface = 0xFFE6E0E9.toInt(), onSurfaceVariant = 0xFFCAC4D0.toInt(),
            accent = 0xFFD0BCFF.toInt(),
        ) else BusymateColors(
            container = 0xFFEADDFF.toInt(), onContainer = 0xFF4F378B.toInt(),
            badge = 0xFFB3261E.toInt(), onBadge = 0xFFFFFFFF.toInt(),
            surface = 0xFFECE6F0.toInt(), onSurface = 0xFF1D1B20.toInt(), onSurfaceVariant = 0xFF49454F.toInt(),
            accent = 0xFF6750A4.toInt(),
        )

        /** The device's Material You palette (Android 12+), mapped to the same roles. */
        @SuppressLint("InlinedApi")
        private fun dynamic(context: Context, night: Boolean): BusymateColors {
            val base = baseline(night)
            fun color(id: Int) = ContextCompat.getColor(context, id)
            return try {
                if (night) BusymateColors(
                    container = color(android.R.color.system_accent1_700), onContainer = color(android.R.color.system_accent1_100),
                    badge = base.badge, onBadge = base.onBadge,
                    surface = color(android.R.color.system_neutral1_800), onSurface = color(android.R.color.system_neutral1_100),
                    onSurfaceVariant = color(android.R.color.system_neutral2_200), accent = color(android.R.color.system_accent1_200),
                ) else BusymateColors(
                    container = color(android.R.color.system_accent1_100), onContainer = color(android.R.color.system_accent1_900),
                    badge = base.badge, onBadge = base.onBadge,
                    surface = color(android.R.color.system_neutral1_50), onSurface = color(android.R.color.system_neutral1_900),
                    onSurfaceVariant = color(android.R.color.system_neutral2_700), accent = color(android.R.color.system_accent1_600),
                )
            } catch (_: Exception) { base }
        }

        /** A Material 3 theme attribute of the host app, by name (the SDK does not depend on the Material library). */
        @SuppressLint("DiscouragedApi")
        private fun themed(context: Context, name: String): Int? {
            val id = context.resources.getIdentifier(name, "attr", context.packageName)
            if (id == 0) return null
            val value = TypedValue()
            if (!context.theme.resolveAttribute(id, value, true)) return null
            return when {
                value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> value.data
                value.resourceId != 0 -> try { ContextCompat.getColor(context, value.resourceId) } catch (_: Exception) { null }
                else -> null
            }
        }
    }
}
