package com.deutschdreamers.mensis.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.deutschdreamers.mensis.ui.theme.MensisTheme

/** Placeholder until the Today screen lands in build step 4. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Mensis", style = MaterialTheme.typography.displaySmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MensisTheme {
        HomeScreen()
    }
}
