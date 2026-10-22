package com.deutschdreamers.mensis.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.navigation.Backup
import com.deutschdreamers.mensis.ui.navigation.Import
import com.deutschdreamers.mensis.ui.navigation.MensisNavKey
import com.deutschdreamers.mensis.ui.screens.calendar.CalendarScreen
import com.deutschdreamers.mensis.ui.screens.insights.InsightsScreen
import com.deutschdreamers.mensis.ui.screens.log.DailyLogHost
import com.deutschdreamers.mensis.ui.screens.log.LogRequest
import com.deutschdreamers.mensis.ui.screens.settings.SettingsScreen
import com.deutschdreamers.mensis.ui.screens.today.TodayScreen

/** The four tabs. Each tab's state (scroll, selection) is kept while another tab is shown. */
@Composable
fun MainScreen(
    onNavigate: (MensisNavKey) -> Unit,
    initialTab: MainTab = MainTab.TODAY,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var logRequest by rememberSaveable(stateSaver = LogRequest.Saver) { mutableStateOf<LogRequest?>(null) }
    val stateHolder = rememberSaveableStateHolder()
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                for (item in MainTab.entries) {
                    val selected = item == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = item },
                        icon = {
                            MsIcon(
                                resId = if (selected) item.iconFilled else item.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(item.labelRes)) },
                        colors =
                            NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            stateHolder.SaveableStateProvider(tab) {
                when (tab) {
                    MainTab.TODAY ->
                        TodayScreen(
                            viewModel = hiltViewModel(),
                            onLog = { request -> logRequest = request },
                            onImport = { onNavigate(Import) },
                            onOpenBackup = { reconnect -> onNavigate(Backup(reconnect = reconnect)) },
                            onMethodSettings = { onNavigate(com.deutschdreamers.mensis.ui.navigation.Contraception) },
                        )
                    MainTab.CALENDAR ->
                        CalendarScreen(
                            viewModel = hiltViewModel(),
                            onLog = { date -> logRequest = LogRequest(date) },
                        )
                    MainTab.INSIGHTS -> InsightsScreen(viewModel = hiltViewModel())
                    MainTab.SETTINGS -> SettingsScreen(viewModel = hiltViewModel(), onNavigate = onNavigate)
                }
            }
        }
    }
    logRequest?.let { request ->
        DailyLogHost(request = request, viewModel = hiltViewModel(), onDismiss = { logRequest = null })
    }
}
