package com.shilapi.xcertplay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.provider.Settings
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat

/** Small platform boundaries shared by the classic View screens. */
internal object LegacyUiCompat {
    fun canDrawOverlays(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(context)
        else ContextCompat.checkSelfPermission(context, Manifest.permission.SYSTEM_ALERT_WINDOW) == PackageManager.PERMISSION_GRANTED

    fun canWriteSettings(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 23) Settings.System.canWrite(context)
        else ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun pressedBackground(content: Drawable, rippleColor: Int): Drawable =
        if (Build.VERSION.SDK_INT >= 21) RippleDrawable(ColorStateList.valueOf(rippleColor), content, null)
        else android.graphics.drawable.StateListDrawable().apply {
            val pressed = content.constantState?.newDrawable()?.mutate() ?: content
            pressed.setColorFilter(rippleColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), content)
        }
}
