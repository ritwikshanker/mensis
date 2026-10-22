package com.deutschdreamers.mensis.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.deutschdreamers.mensis.ui.screens.about.AboutScreen
import com.deutschdreamers.mensis.ui.screens.about.NoticesScreen
import com.deutschdreamers.mensis.ui.screens.backup.BackupScreen
import com.deutschdreamers.mensis.ui.screens.contraception.ContraceptionScreen
import com.deutschdreamers.mensis.ui.screens.export.ExportScreen
import com.deutschdreamers.mensis.ui.screens.importer.ImportScreen
import com.deutschdreamers.mensis.ui.screens.main.MainScreen
import com.deutschdreamers.mensis.ui.screens.mode.ModeScreen
import com.deutschdreamers.mensis.ui.screens.onboarding.OnboardingScreen
import com.deutschdreamers.mensis.ui.screens.reminders.RemindersScreen
import com.deutschdreamers.mensis.ui.screens.report.ReportScreen
import com.deutschdreamers.mensis.ui.screens.restore.RestoreScreen
import com.deutschdreamers.mensis.ui.screens.theme.ThemeScreen
import com.deutschdreamers.mensis.ui.screens.track.TrackLevelScreen
import com.deutschdreamers.mensis.ui.screens.units.UnitsScreen

@Composable
fun MensisNavDisplay(startAtOnboarding: Boolean) {
    val backStack = rememberNavBackStack(if (startAtOnboarding) Onboarding else Main)
    val navigator = remember(backStack) { Navigator(backStack) }
    NavDisplay(
        backStack = backStack,
        onBack = { navigator.pop() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<Onboarding> {
                    OnboardingScreen(
                        onFinished = { navigator.replaceAll(Main) },
                        onRestore = { navigator.push(Restore(fromOnboarding = true)) },
                    )
                }
                entry<Main> {
                    MainScreen(
                        onNavigate = navigator::push,
                        // Everything is gone: start again at onboarding step 1 with nothing to go back to.
                        onDataDeleted = { navigator.replaceAll(Onboarding) },
                    )
                }
                entry<TrackLevel> { TrackLevelScreen(onBack = navigator::pop) }
                entry<Contraception> {
                    ContraceptionScreen(
                        onBack = navigator::pop,
                        onReminders = { navigator.push(Reminders) },
                    )
                }
                entry<Reminders> { RemindersScreen(onBack = navigator::pop) }
                entry<Export> { ExportScreen(onBack = navigator::pop) }
                entry<Import> { ImportScreen(onBack = navigator::pop) }
                entry<Report> { ReportScreen(onBack = navigator::pop) }
                entry<Backup> { key ->
                    BackupScreen(
                        onBack = navigator::pop,
                        onRestore = { navigator.push(Restore(fromOnboarding = false)) },
                        reconnect = key.reconnect,
                    )
                }
                entry<Restore> { key ->
                    RestoreScreen(
                        onClose = navigator::pop,
                        onRestored = { navigator.replaceAll(Main) },
                        fromOnboarding = key.fromOnboarding,
                    )
                }
                entry<Mode> { ModeScreen(onBack = navigator::pop) }
                entry<MeasurementUnits> { UnitsScreen(onBack = navigator::pop) }
                entry<Theme> { ThemeScreen(onBack = navigator::pop) }
                entry<About> { AboutScreen(onBack = navigator::pop, onNotices = { navigator.push(Notices(it)) }) }
                entry<Notices> { key -> NoticesScreen(document = key.document, onBack = navigator::pop) }
            },
    )
}
