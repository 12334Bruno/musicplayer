package com.example.musicplayer

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController

@Composable
fun navBar(
    activityName: String,
    navController: NavHostController,
    sharedPrefs: SharedPreferences,
    navBarHeight: Int,
    disabledNavbar: Boolean = false,
) {
    // Navbar row 2
    Column(modifier = Modifier.background(Color(purpleGrey)).height(navBarHeight.dp)) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .background(Color(purpleGrey))
        ) {
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                Text(
                    modifier = Modifier
                        .clickable {
                            sharedPrefs.edit().putBoolean("loggedIn", false).apply()
                            navController.navigate(NavGraph.Login.route)
                        }
                        .padding(8.dp, 12.dp, 16.dp, 6.dp)
                    ,
                    text="Log out",
                    color = Color(lightPurple),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp, 16.dp, 8.dp)
                .background(Color(purpleGrey)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Add your navigation items here
            buildClickableText("Songs", NavGraph.Home.route, activityName, navController, disabledNavbar)
            buildClickableText("Albums", NavGraph.Albums.route, activityName, navController, disabledNavbar)
            buildClickableText("Artists", NavGraph.Artists.route, activityName, navController, disabledNavbar)
            buildClickableText("Playlists", NavGraph.Playlists.route, activityName, navController, disabledNavbar)
        }
    }
}

@Composable
fun buildClickableText(
    label: String,
    route: String,
    activityName: String,
    navController: NavController,
    disabledNavbar: Boolean,
) {
    // Show active/disabled navigation using color change
    var activeColor = Color(lightPurple)
    if (disabledNavbar) {activeColor = Color(grey)}

    Text(
        text = label,
        color = activeColor,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable {
            if (activityName != label && !disabledNavbar) {
                navController.navigate(route)
            }
        }.drawUnderline(activityName == label) ,
    )
}


@Composable
fun Modifier.drawUnderline(underline: Boolean): Modifier {
    return drawBehind {
        var heightOffSet = 26f
        if (underline) {
            drawLine(
                color = Color(lightPurple),
                start = Offset(10f, size.height + heightOffSet), // Adjust the height and positioning
                end = Offset(size.width -10f, size.height + heightOffSet), // Adjust the height and positioning
                strokeWidth = 20f, // Adjust thickness as needed
                cap = StrokeCap.Round
            )

            // Draw a white line to hide the bottom part
            drawLine(
                color = Color(darkGrey),
                start = Offset(-10f, size.height+ heightOffSet + 10f),
                end = Offset(size.width + 10f, size.height+ heightOffSet + 10f),
                strokeWidth = 20f, // Adjust thickness to match the top line
            )
        }
    }
}