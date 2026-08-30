package com.example.firestationops

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource

import firestationops.app.shared.generated.resources.Res
import firestationops.app.shared.generated.resources.compose_multiplatform

import com.example.firestationops.domain.model.UserState
import com.example.firestationops.domain.repository.mock.MockAuthRepository
import com.example.firestationops.domain.repository.mock.MockApparatusRepository
import com.example.firestationops.ui.auth.LoginScreen
import com.example.firestationops.ui.auth.LoginViewModel
import com.example.firestationops.ui.dashboard.DashboardScreen
import com.example.firestationops.ui.dashboard.DashboardViewModel

@Composable
@Preview
fun App() {
    val authRepository = remember { MockAuthRepository() }
    val apparatusRepository = remember { MockApparatusRepository() }
    val loginViewModel = remember { LoginViewModel(authRepository) }
    val userState by loginViewModel.userState.collectAsState()

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val state = userState) {
                is UserState.Authenticated -> {
                    val dashboardViewModel = remember(state.member.departmentId) {
                        DashboardViewModel(state.member.departmentId, apparatusRepository)
                    }
                    MainContent(
                        member = state.member,
                        onLogout = loginViewModel::logout,
                        content = { DashboardScreen(dashboardViewModel) }
                    )
                }
                UserState.Unauthenticated, is UserState.Loading, is UserState.Error -> {
                    LoginScreen(viewModel = loginViewModel)
                }
            }
        }
    }
}

@Composable
fun MainContent(
    member: com.example.firestationops.domain.model.Member,
    onLogout: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
        
        // Simple Bottom Bar for Logout and Info
        Surface(
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeContentPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = member.fullName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Department ID: ${member.departmentId}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Logout")
                }
            }
        }
    }
}

