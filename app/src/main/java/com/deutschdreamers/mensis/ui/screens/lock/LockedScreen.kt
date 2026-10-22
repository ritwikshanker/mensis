package com.deutschdreamers.mensis.ui.screens.lock

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.lock.BiometricUnlock
import com.deutschdreamers.mensis.ui.theme.MensisTheme

/**
 * Covers [content] with the Locked screen while [locked]. The content stays composed (its state
 * survives the lock) but is hidden from accessibility services and cannot be touched; back on the
 * Locked screen leaves the app instead of popping the hidden back stack.
 */
@Composable
fun LockGate(
    locked: Boolean,
    onUnlocked: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val hidden = if (locked) Modifier.semantics { hideFromAccessibility() } else Modifier
        Box(modifier = Modifier.fillMaxSize().then(hidden)) { content() }
        if (locked) {
            val activity = LocalActivity.current
            BackHandler { activity?.moveTaskToBack(true) }
            LockedScreen(onUnlocked = onUnlocked)
        }
    }
}

/** Design §2 "Locked". The system prompt opens once on first show and again from the fingerprint tile. */
@Composable
fun LockedScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as? FragmentActivity
    val currentOnUnlocked by rememberUpdatedState(onUnlocked)
    var error by remember { mutableStateOf<CharSequence?>(null) }
    val unlock =
        remember(activity) {
            activity?.let {
                BiometricUnlock(
                    activity = it,
                    onUnlocked = { currentOnUnlocked() },
                    onError = { message -> error = message },
                )
            }
        }
    var promptShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(unlock) {
        if (unlock != null && !promptShown) {
            promptShown = true
            unlock.authenticate()
        }
    }
    LockedScreenContent(
        error = error,
        onUnlockClick = { unlock?.authenticate() },
        onUsePinClick = { unlock?.authenticate(credentialOnly = true) },
        modifier = modifier,
    )
}

@Composable
private fun LockedScreenContent(
    error: CharSequence?,
    onUnlockClick: () -> Unit,
    onUsePinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ms_lock),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.locked_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.locked_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 260.dp),
            )
            Spacer(modifier = Modifier.height(40.dp))
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(role = Role.Button, onClick = onUnlockClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ms_fingerprint),
                    contentDescription = stringResource(R.string.locked_unlock),
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onUsePinClick) {
                Text(text = stringResource(R.string.locked_use_pin))
            }
            if (error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LockedScreenPreview() {
    MensisTheme {
        LockedScreenContent(error = null, onUnlockClick = {}, onUsePinClick = {})
    }
}
