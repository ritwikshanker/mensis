package com.deutschdreamers.mensis.ui.screens.today

import androidx.annotation.DrawableRes
import com.deutschdreamers.mensis.domain.today.CycleStrip
import com.deutschdreamers.mensis.domain.today.StripLaneKind
import com.deutschdreamers.mensis.domain.today.TodayState
import java.time.LocalDate

/** Everything the Today screen renders, already worded. Built by [TodayUiBuilder] off the main thread. */
data class TodayUiState(
    val today: LocalDate,
    val state: TodayState,
    val dateLabel: String,
    val banner: BannerUi?,
    val numeral: String,
    val numeralLabel: String,
    val phase: String,
    val supporting: String,
    val strip: CycleStrip?,
    val stripHelper: String?,
    val laneLabels: Map<StripLaneKind, String>,
    val cards: List<RangeCardUi>,
    val lateCard: LateCardUi?,
    val firstLaunchCard: Boolean,
    val methodCard: MethodCardUi?,
    val pregnancyCard: PregnancyCardUi?,
    val logged: List<ValueChipUi>,
)

enum class BannerKind { OFF, FAILING, OVERDUE, RECONNECT }

data class BannerUi(
    val kind: BannerKind,
    @param:DrawableRes val icon: Int,
    val text: String,
    val action: String,
)

enum class CardAccent { PERIOD, FERTILE, NEUTRAL }

data class RangeCardUi(
    @param:DrawableRes val icon: Int,
    val title: String,
    val range: String,
    val supporting: String,
    val pips: Int,
    val basis: String,
    val accent: CardAccent,
)

data class LateCardUi(
    val body: String,
)

data class MethodCardUi(
    val title: String,
    val sinceLabel: String,
    val since: String,
    val nextLabel: String,
    val next: String,
    val body: String,
)

data class PregnancyCardUi(
    val dueDate: String,
    val trimester: String,
    val progress: Float,
    val caption: String,
)

data class ValueChipUi(
    @param:DrawableRes val icon: Int?,
    val text: String,
)
