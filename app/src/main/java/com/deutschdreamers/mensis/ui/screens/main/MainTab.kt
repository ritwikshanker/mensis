package com.deutschdreamers.mensis.ui.screens.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.deutschdreamers.mensis.R

enum class MainTab(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val iconFilled: Int,
) {
    TODAY(R.string.tab_today, R.drawable.ms_today, R.drawable.ms_today_fill),
    CALENDAR(R.string.tab_calendar, R.drawable.ms_calendar_month, R.drawable.ms_calendar_month_fill),
    INSIGHTS(R.string.tab_insights, R.drawable.ms_monitoring, R.drawable.ms_monitoring_fill),
    SETTINGS(R.string.tab_settings, R.drawable.ms_settings, R.drawable.ms_settings_fill),
}
