package com.rayan.gametimelimiter.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The desktop app's stroke icon set (24x24, 2px strokes). */
object Ic {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = PathParser().parsePathString(it).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

    val Home by lazy { icon("home", "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z") }
    val Chart by lazy { icon("chart", "M4 20V10M10 20V4M16 20v-7M22 20H2") }
    val Settings by lazy {
        icon(
            "settings",
            circle(12f, 12f, 3f),
            "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
        )
    }
    val Plus by lazy { icon("plus", "M12 5v14M5 12h14") }
    val Edit by lazy { icon("edit", "M12 20h9", "M16.5 3.5a2.1 2.1 0 1 1 3 3L7 19l-4 1 1-4z") }
    val Trash by lazy { icon("trash", "M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6") }
    val Lock by lazy { icon("lock", "M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M8 11V7a4 4 0 0 1 8 0v4") }
    val Pause by lazy { icon("pause", "M7 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M15 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-2a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z") }
    val Play by lazy { icon("play", "M6 4l14 8-14 8z") }
    val Refresh by lazy { icon("refresh", "M21 12a9 9 0 1 1-3-6.7L21 8", "M21 3v5h-5") }
    val X by lazy { icon("x", "M18 6 6 18M6 6l12 12") }
    val Search by lazy { icon("search", circle(11f, 11f, 7f), "m20 20-3.5-3.5") }
    val Bell by lazy { icon("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.9 1.9 0 0 0 3.4 0") }
    val Clock by lazy { icon("clock", circle(12f, 12f, 9f), "M12 7v5l3 2") }
    val Gamepad by lazy { icon("gamepad", "M6 11h4M8 9v4M15 12h.01M18 10h.01", "M17.3 5H6.7a4 4 0 0 0-4 3.6L2 15a3 3 0 0 0 5.2 2.1L9 15h6l1.8 2.1A3 3 0 0 0 22 15l-.7-6.4a4 4 0 0 0-4-3.6z") }
    val Shield by lazy { icon("shield", "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z") }
    val Power by lazy { icon("power", "M18.4 6.6a9 9 0 1 1-12.8 0M12 2v10") }
    val Check by lazy { icon("check", "M20 6 9 17l-5-5") }
    val Alert by lazy { icon("alert", "M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z", "M12 9v4M12 17h.01") }
    val Apps by lazy { icon("apps", "M5 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M15 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M5 14h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1z", "M15 14h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1z") }
    val Battery by lazy { icon("battery", "M4 7h13a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z", "M22 11v2", "M10 9l-2 3h4l-2 3") }
    val Eye by lazy { icon("eye", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", circle(12f, 12f, 3f)) }
    val Layers by lazy { icon("layers", "M12 2 2 7l10 5 10-5z", "M2 17l10 5 10-5M2 12l10 5 10-5") }
    val ChevronRight by lazy { icon("chevron", "m9 18 6-6-6-6") }
    val Minus by lazy { icon("minus", "M5 12h14") }
}
