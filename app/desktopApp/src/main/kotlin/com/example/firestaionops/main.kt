package com.example.firestaionops

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "FirestaionOps",
    ) {
        App()
    }
}