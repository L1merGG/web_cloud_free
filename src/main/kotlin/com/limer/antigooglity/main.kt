package com.limer.antigooglity

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.limer.antigooglity.coordinator.AgentCoordinator
import com.limer.antigooglity.ui.MainScreen
import java.nio.file.Path

fun main() = application {
    val initialWorkspace = remember {
        Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    }
    val coordinator = remember {
        AgentCoordinator(initialWorkspacePath = initialWorkspace)
    }

    Window(
        onCloseRequest = {
            coordinator.cancelTask()
            exitApplication()
        },
        title = "Antigooglity — Autonomous AI Agent",
        state = WindowState(width = 1180.dp, height = 780.dp),
    ) {
        MainScreen(coordinator = coordinator)
    }
}
