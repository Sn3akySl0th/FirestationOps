package com.example.firestationops.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.Station

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val stations by viewModel.stations.collectAsState()
    val apparatus by viewModel.apparatus.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Operations Dashboard", style = MaterialTheme.typography.headlineMedium)
        }

        items(stations) { station ->
            StationCard(
                station = station,
                apparatusList = apparatus.filter { it.stationId == station.id }
            )
        }
    }
}

@Composable
fun StationCard(station: Station, apparatusList: List<Apparatus>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(station.name, style = MaterialTheme.typography.titleLarge)
            station.address?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
            if (apparatusList.isEmpty()) {
                Text("No apparatus assigned", style = MaterialTheme.typography.bodyMedium)
            } else {
                apparatusList.forEach { app ->
                    ApparatusItem(app)
                }
            }
        }
    }
}

@Composable
fun ApparatusItem(apparatus: Apparatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: Navigate to inspection */ }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(apparatus.radioName, style = MaterialTheme.typography.titleMedium)
            Text(apparatus.type, style = MaterialTheme.typography.bodySmall)
        }
        
        StatusBadge(apparatus.status)
    }
}

@Composable
fun StatusBadge(status: ApparatusStatus) {
    val color = when (status) {
        ApparatusStatus.IN_SERVICE -> Color(0xFF4CAF50) // Green
        ApparatusStatus.OUT_OF_SERVICE -> MaterialTheme.colorScheme.error
        ApparatusStatus.MAINTENANCE -> Color(0xFFFF9800) // Orange
        ApparatusStatus.RESERVE -> Color(0xFF2196F3) // Blue
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (status == ApparatusStatus.OUT_OF_SERVICE) "⚠ " else "",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
            Text(
                text = status.name.replace("_", " "),
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}
