package com.aegis.mobile.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aegis.mobile.ui.home.HomeScreen
import com.aegis.mobile.ui.sos.SosScreen
import com.aegis.mobile.ui.broadcast.BroadcastScreen
import com.aegis.mobile.ui.private.PrivateMessageScreen
import com.aegis.mobile.ui.meshstatus.MeshStatusScreen

object Routes {
    const val HOME = "home"
    const val SOS = "sos"
    const val BROADCAST = "broadcast"
    const val PRIVATE = "private"
    const val MESH_STATUS = "mesh_status"
}

@Composable
fun AegisNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onSosClick = { navController.navigate(Routes.SOS) },
                onBroadcastClick = { navController.navigate(Routes.BROADCAST) },
                onPrivateClick = { navController.navigate(Routes.PRIVATE) },
                onMeshStatusClick = { navController.navigate(Routes.MESH_STATUS) }
            )
        }
        composable(Routes.SOS) {
            SosScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BROADCAST) {
            BroadcastScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PRIVATE) {
            PrivateMessageScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MESH_STATUS) {
            MeshStatusScreen(onBack = { navController.popBackStack() })
        }
    }
}
