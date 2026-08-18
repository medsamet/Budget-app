package com.medsamet.budgetapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private enum class Tab(val label: String, val icon: ImageVector) {
    EXPENSES("Dépenses", Icons.Filled.ShoppingCart),
    STATS("Stats", Icons.Filled.BarChart),
    FORECAST("Prévisions", Icons.Filled.TrendingUp),
    EVENTS("Agenda", Icons.Filled.Event),
    DATA("Données", Icons.Filled.Storage)
}

@Composable
fun RootScreen(viewModel: BudgetViewModel) {
    var selected by rememberSaveable { mutableStateOf(Tab.EXPENSES.name) }
    val snackbarHostState = remember { SnackbarHostState() }

    val message = viewModel.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                for (tab in Tab.values()) {
                    NavigationBarItem(
                        selected = selected == tab.name,
                        onClick = { selected = tab.name },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selected) {
                Tab.EXPENSES.name -> ExpensesScreen(viewModel)
                Tab.STATS.name -> StatsScreen(viewModel)
                Tab.FORECAST.name -> ForecastScreen(viewModel)
                Tab.EVENTS.name -> EventsScreen(viewModel)
                Tab.DATA.name -> DataScreen(viewModel)
                else -> ExpensesScreen(viewModel)
            }
        }
    }
}
