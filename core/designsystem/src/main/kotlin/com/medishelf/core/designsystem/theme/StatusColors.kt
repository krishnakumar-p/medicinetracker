package com.medishelf.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors for medicine status chips/badges. Always pair color with an icon or
 * text label so status is never conveyed by color alone (accessibility).
 */
@Immutable
data class StatusColors(
    val expiredContainer: Color,
    val onExpiredContainer: Color,
    val expiringContainer: Color,
    val onExpiringContainer: Color,
    val okContainer: Color,
    val onOkContainer: Color,
    val lowStockContainer: Color,
    val onLowStockContainer: Color,
)

internal val LightStatusColors = StatusColors(
    expiredContainer = Color(0xFFFFDAD6),
    onExpiredContainer = Color(0xFF410002),
    expiringContainer = Color(0xFFFFDEA6),
    onExpiringContainer = Color(0xFF271900),
    okContainer = Color(0xFFCCE8E2),
    onOkContainer = Color(0xFF05201C),
    lowStockContainer = Color(0xFFCCE5FF),
    onLowStockContainer = Color(0xFF001E31),
)

internal val DarkStatusColors = StatusColors(
    expiredContainer = Color(0xFF93000A),
    onExpiredContainer = Color(0xFFFFDAD6),
    expiringContainer = Color(0xFF5C4200),
    onExpiringContainer = Color(0xFFFFDEA6),
    okContainer = Color(0xFF334B47),
    onOkContainer = Color(0xFFCCE8E2),
    lowStockContainer = Color(0xFF2D4961),
    onLowStockContainer = Color(0xFFCCE5FF),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
