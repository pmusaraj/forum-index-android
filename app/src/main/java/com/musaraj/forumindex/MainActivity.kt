package com.musaraj.forumindex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ForumIndexApp() }
    }
}

@Composable
private fun ForumIndexApp() {
    val background = colorResource(R.color.background)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = colorResource(R.color.accent),
            background = background,
            surface = background,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = background) {}
    }
}
