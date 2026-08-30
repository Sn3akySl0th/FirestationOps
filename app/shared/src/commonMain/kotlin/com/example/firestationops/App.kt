package com.example.firestationops

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import com.example.firestationops.ui.auth.LoginScreen
import com.example.firestationops.ui.auth.LoginViewModel

@Composable
@Preview
fun App() {
    val authRepository = remember { MockAuthRepository() }
    val loginViewModel = remember { LoginViewModel(authRepository) }
    val userState by loginViewModel.userState.collectAsState()

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val state = userState) {
                is UserState.Authenticated -> {
                    MainContent(state.member, onLogout = loginViewModel::logout)
                }
                UserState.Unauthenticated, is UserState.Loading, is UserState.Error -> {
                    LoginScreen(viewModel = loginViewModel)
                }
            }
        }
    }
}

@Composable
fun MainContent(member: com.example.firestationops.domain.model.Member, onLogout: () -> Unit) {
    var showContent by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer)
            .safeContentPadding()
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Welcome, ${member.fullName}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )
        
        Button(onClick = { showContent = !showContent }) {
            Text("Toggle Sample Content")
        }
        
        AnimatedVisibility(showContent) {
            val greeting = remember { Greeting().greet() }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(painterResource(Res.drawable.compose_multiplatform), null)
                Text("Compose: $greeting")
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onLogout,
            modifier = Modifier.padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Logout")
        }
    }
}
