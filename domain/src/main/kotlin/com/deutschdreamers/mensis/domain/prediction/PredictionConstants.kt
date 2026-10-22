package com.deutschdreamers.mensis.domain.prediction

import java.time.LocalTime

/**
 * Every threshold the prediction engine uses, in one place so that a biologist can review them.
 * Values are deliberately conservative; each one says what it does and why.
 *
 * Predictions are statistics about the user's own history. They are never a method of contraception.
 */
object PredictionConstants {
    // ---- Period and cycle derivation -------------------------------------------------------------

    /**
     * A day with flow of at least "light" starts a new period when the previous light-or-heavier day is
     * more than this many days earlier. Bleeding days closer together belong to the same period.
     * Spotting never starts a period.
     */
    const val PERIOD_GAP_DAYS: Int = 5

    /**
     * When counting period length, bleeding days (spotting or more) separated by at most this many
     * missing days are treated as one run. Lets a single forgotten day not cut a period short.
     */
    const val PERIOD_RUN_MAX_GAP_DAYS: Int = 1

    // ---- Next-period prediction -------------------------------------------------------------------

    /** Predictions rest on at most this many of the most recent complete cycles. */
    const val PREDICTION_WINDOW_CYCLES: Int = 6

    /** Cycle lengths below this are treated as outliers (e.g. mid-cycle bleeding logged as flow). */
    const val CYCLE_LENGTH_MIN_DAYS: Int = 18

    /** Cycle lengths above this are treated as outliers (e.g. a forgotten period). */
    const val CYCLE_LENGTH_MAX_DAYS: Int = 50

    /**
     * Robust outlier rule on top of the absolute bounds: a length further than this many scaled
     * median absolute deviations from the median is discarded. 1.4826 scales the MAD to a standard
     * deviation for normal data; 3 is the usual "far out" multiplier.
     */
    const val MAD_OUTLIER_MULTIPLIER: Double = 3.0

    /** The MAD is never taken below this (in days), so a run of identical cycles does not reject everything. */
    const val MAD_FLOOR_DAYS: Double = 1.0

    /** Scale factor that turns a median absolute deviation into a standard deviation for normal data. */
    const val MAD_NORMAL_SCALE: Double = 1.4826

    /**
     * Recency weight: the k-th most recent kept cycle gets weight `RECENCY_DECAY^k` when averaging.
     * 0.8 means the oldest of six cycles still counts about a third as much as the newest.
     */
    const val RECENCY_DECAY: Double = 0.8

    /** Spread (±days) around the most likely date is at least this. */
    const val MIN_SPREAD_DAYS: Int = 1

    /** Spread used while fewer than [MIN_CYCLES_FOR_HISTORY] cycles exist and onboarding lengths are used. */
    const val DEFAULT_SPREAD_DAYS: Int = 3

    /** Below this many kept cycles the onboarding typical length is used instead of history. */
    const val MIN_CYCLES_FOR_HISTORY: Int = 2

    /** Extra spread when the recent cycles are irregular. */
    const val IRREGULAR_EXTRA_SPREAD_DAYS: Int = 1

    /** Cycles are called irregular when the longest minus the shortest kept cycle exceeds this. */
    const val IRREGULAR_SPAN_DAYS: Int = 9

    /** Each missed pill in the current pack widens the withdrawal-bleed range by this many days. */
    const val MISSED_PILL_WIDEN_DAYS: Int = 1

    /** Emergency contraception this cycle widens the predicted range by this many days. */
    const val EMERGENCY_CONTRACEPTION_WIDEN_DAYS: Int = 2

    /** The period is "late" once today is more than this many days past the latest predicted date. */
    const val LATE_GRACE_DAYS: Int = 2

    // ---- Confidence pips ------------------------------------------------------------------------

    /** Kept cycles needed for one pip (any history at all). */
    const val PIPS_ONE_MIN_CYCLES: Int = 1

    /** Kept cycles needed for two pips. */
    const val PIPS_TWO_MIN_CYCLES: Int = 3

    /** Kept cycles needed for three pips: a full prediction window. */
    const val PIPS_THREE_MIN_CYCLES: Int = PREDICTION_WINDOW_CYCLES

    // ---- Ovulation, luteal phase, fertile window --------------------------------------------------

    /** Luteal length assumed until the user's confirmed ovulations teach a personal value. */
    const val DEFAULT_LUTEAL_DAYS: Int = 14

    /** Confirmed ovulations needed before the personal luteal length replaces the default. */
    const val LUTEAL_MIN_CONFIRMATIONS: Int = 2

    /** Learned luteal lengths are clamped to this range (physiological bounds). */
    const val LUTEAL_MIN_DAYS: Int = 10
    const val LUTEAL_MAX_DAYS: Int = 17

    /** The fertile window starts this many days before estimated ovulation and ends on ovulation day. */
    const val FERTILE_WINDOW_DAYS_BEFORE_OVULATION: Int = 5

    /** Ovulation after a positive LH test: this many days after the first positive (earliest). */
    const val LH_OVULATION_OFFSET_MIN_DAYS: Int = 1

    /** Ovulation after a positive LH test: at most this many days after the first positive. */
    const val LH_OVULATION_OFFSET_MAX_DAYS: Int = 2

    // ---- Basal body temperature (3-over-6 rule) ---------------------------------------------------

    /** Readings compared with the highest of this many preceding valid readings. */
    const val BBT_BASELINE_READINGS: Int = 6

    /** This many consecutive elevated readings confirm a shift. */
    const val BBT_ELEVATED_READINGS: Int = 3

    /** Each elevated reading must exceed the baseline maximum by at least this (°C). */
    const val BBT_SHIFT_MARGIN_C: Double = 0.1

    /** The third elevated reading must exceed the coverline by at least this (°C), as in the sympto-thermal method. */
    const val BBT_THIRD_READING_MIN_RISE_C: Double = 0.2

    /** The three elevated readings must fall within this many calendar days (one missing day tolerated). */
    const val BBT_ELEVATED_MAX_SPAN_DAYS: Int = 4

    /** Readings taken at or after this time are flagged in the chart and skipped by the rule. */
    val BBT_LATE_CUTOFF: LocalTime = LocalTime.of(9, 0)

    // ---- Contraception ----------------------------------------------------------------------------

    /** Pack length for combined pill 21+7, patch (3+1 weeks) and ring (3+1 weeks). */
    const val PACK_LENGTH_DAYS: Int = 28

    /** Active days in a 21+7 pack. */
    const val PACK_ACTIVE_DAYS: Int = 21

    /** Withdrawal bleeding usually starts this many days into the break (first break day = 1). */
    const val WITHDRAWAL_BLEED_START_BREAK_DAY: Int = 2

    /** ... and ends around this break day. */
    const val WITHDRAWAL_BLEED_END_BREAK_DAY: Int = 5

    /** Complete packs with logged bleeding needed before the personal bleed offset replaces the default. */
    const val WITHDRAWAL_MIN_PACKS_FOR_HISTORY: Int = 2

    /** Injection interval (weeks). */
    const val INJECTION_INTERVAL_WEEKS: Int = 12

    /** Default implant replacement interval (years); editable per method period. */
    const val IMPLANT_YEARS: Int = 3

    /** Default hormonal IUD replacement interval (years); editable (types range 3–8). */
    const val HORMONAL_IUD_YEARS: Int = 5

    /** Default copper IUD replacement interval (years); editable (types range 5–10). */
    const val COPPER_IUD_YEARS: Int = 10

    /** The suppressed-method summary counts spotting days over this many days. */
    const val SUPPRESSED_SPOTTING_WINDOW_DAYS: Int = 90

    // ---- Pregnancy --------------------------------------------------------------------------------

    /** Naegele's rule: due date = first day of the last period + 280 days. */
    const val PREGNANCY_DURATION_DAYS: Int = 280

    /** First trimester ends after this week (inclusive). */
    const val TRIMESTER_1_LAST_WEEK: Int = 13

    /** Second trimester ends after this week (inclusive). */
    const val TRIMESTER_2_LAST_WEEK: Int = 27

    // ---- Insights ---------------------------------------------------------------------------------

    /** Insights means and ranges use the last this many complete cycles. */
    const val INSIGHTS_WINDOW_CYCLES: Int = 8

    /** Today's strip shows at least this many days. */
    const val STRIP_MIN_DAYS: Int = 32

    /** A symptom pattern is reported when it occurs in at least this fraction of the considered cycles. */
    const val SYMPTOM_PATTERN_MIN_FRACTION: Double = 0.5

    /** Days before the next period that count as "the days before a period" for symptom patterns. */
    const val SYMPTOM_PRE_PERIOD_DAYS: Int = 3

    /** Days before the next period that count as "late luteal" for symptom patterns. */
    const val SYMPTOM_LATE_LUTEAL_DAYS: Int = 7
}
