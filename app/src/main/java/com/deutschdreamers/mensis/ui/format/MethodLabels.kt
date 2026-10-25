package com.deutschdreamers.mensis.ui.format

import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.MethodKind
import com.deutschdreamers.mensis.domain.model.Regimen

/** Design §9 method names, descriptions and the "what this changes" copy per kind. */
object MethodLabels {
    fun name(method: ContraceptionMethod): String =
        when (method) {
            ContraceptionMethod.NONE -> "None or barrier"
            ContraceptionMethod.COMBINED_PILL -> "Combined pill"
            ContraceptionMethod.PROGESTIN_ONLY_PILL -> "Progestin-only pill"
            ContraceptionMethod.PATCH -> "Patch"
            ContraceptionMethod.RING -> "Vaginal ring"
            ContraceptionMethod.INJECTION -> "Injection"
            ContraceptionMethod.IMPLANT -> "Implant"
            ContraceptionMethod.HORMONAL_IUD -> "Hormonal IUD"
            ContraceptionMethod.COPPER_IUD -> "Copper IUD"
        }

    fun description(method: ContraceptionMethod): String =
        when (method) {
            ContraceptionMethod.NONE -> "Condoms, diaphragm, withdrawal, or no method"
            ContraceptionMethod.COMBINED_PILL -> "Oestrogen and progestin, one a day"
            ContraceptionMethod.PROGESTIN_ONLY_PILL -> "One a day, within a 3-hour window"
            ContraceptionMethod.PATCH -> "Weekly for 3 weeks, then 1 week off"
            ContraceptionMethod.RING -> "3 weeks in, 1 week out"
            ContraceptionMethod.INJECTION -> "Every 12 to 13 weeks"
            ContraceptionMethod.IMPLANT -> "Up to 3 years"
            ContraceptionMethod.HORMONAL_IUD -> "3 to 8 years, depending on type"
            ContraceptionMethod.COPPER_IUD -> "5 to 10 years. No hormones; the cycle continues"
        }

    fun effect(kind: MethodKind): String =
        when (kind) {
            MethodKind.NONE -> "Period and fertile-window predictions use your logged cycles as usual."
            MethodKind.COPPER ->
                "No hormonal effect, so predictions work as usual. Periods may be heavier or longer; " +
                    "this is recorded, not flagged."
            MethodKind.LOCAL_HORMONAL ->
                "Ovulation and periods often continue with a hormonal IUD, so period and fertile-window " +
                    "predictions follow the periods you log. Bleeding may become lighter, irregular or stop; " +
                    "until a period comes, Today shows days since the last bleed."
            MethodKind.CYCLIC ->
                "The break-week bleed is predicted instead of a period. Fertile window and ovulation are hidden " +
                    "because ovulation is suppressed. Temperature can still be logged but is not used to confirm " +
                    "ovulation."
            MethodKind.SUPPRESS ->
                "Period and fertile-window predictions are paused; Today shows days since the last bleed. " +
                    "Bleeding you log is kept and shown as spotting."
        }

    fun regimenHelp(regimen: Regimen): String =
        when (regimen) {
            Regimen.CYCLIC ->
                "21 active days, 7-day break. The withdrawal bleed in the break is predicted instead of a period."
            Regimen.CONTINUOUS -> "No break week. Bleed predictions are paused; spotting is common in the first months."
        }

    /** Settings row summary: "Combined pill · cyclic". */
    fun summary(
        method: ContraceptionMethod,
        regimen: Regimen?,
    ): String =
        when {
            method == ContraceptionMethod.NONE -> name(method)
            method.baseKind == MethodKind.CYCLIC ->
                "${name(method)} ${Formats.MIDDLE_DOT} ${if (regimen == Regimen.CONTINUOUS) "continuous" else "cyclic"}"
            else -> name(method)
        }

    /** Label of the date field: "Started", "Inserted" or "Last injection". */
    fun startLabel(method: ContraceptionMethod): String =
        when {
            method == ContraceptionMethod.INJECTION -> "Last injection"
            method.isLongActing -> "Inserted"
            else -> "Started"
        }

    /** "Next pack" / "Next patch" / "Next ring" card title for cyclic methods. */
    fun nextCycleTitle(method: ContraceptionMethod): String =
        when (method) {
            ContraceptionMethod.PATCH -> "Next patch"
            ContraceptionMethod.RING -> "Next ring"
            else -> "Next pack"
        }

    /** "7-day break" / "patch-free week" / "ring-free week". */
    fun breakName(method: ContraceptionMethod): String =
        when (method) {
            ContraceptionMethod.PATCH -> "patch-free week"
            ContraceptionMethod.RING -> "ring-free week"
            else -> "7-day break"
        }

    /** Daily-log helper under the pill segmented control. */
    fun pillHelp(method: ContraceptionMethod): String =
        if (method == ContraceptionMethod.PROGESTIN_ONLY_PILL) {
            "Late = more than 3 hours. A late or missed pill reduces protection for 48 hours."
        } else {
            "Late = more than 12 hours; missed = more than 24. Missed pills widen the bleed prediction."
        }
}
