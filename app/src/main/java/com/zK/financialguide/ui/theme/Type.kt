package com.zK.financialguide.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** A compact Material type scale; zero tracking is also applied to fields and navigation. */
val Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(letterSpacing = 0.sp),
        displayMedium = displayMedium.copy(letterSpacing = 0.sp),
        displaySmall = displaySmall.copy(letterSpacing = 0.sp),
        headlineLarge = headlineLarge.copy(letterSpacing = 0.sp),
        headlineMedium = headlineMedium.copy(fontSize = 28.sp, lineHeight = 36.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        headlineSmall = headlineSmall.copy(letterSpacing = 0.sp),
        titleLarge = titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(letterSpacing = 0.sp),
        bodyLarge = bodyLarge.copy(letterSpacing = 0.sp),
        bodyMedium = bodyMedium.copy(letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(letterSpacing = 0.sp),
        labelLarge = labelLarge.copy(letterSpacing = 0.sp),
        labelMedium = labelMedium.copy(letterSpacing = 0.sp),
        labelSmall = labelSmall.copy(letterSpacing = 0.sp)
    )
}
