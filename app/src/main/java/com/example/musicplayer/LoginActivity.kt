package com.example.musicplayer

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.AuthenticateUserByName


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavHostController, reactiveState: ReactiveState) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf("") }
    val applicationContext = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(darkGrey))
            .padding(16.dp)
        ,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Username Input
        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Text
            ),
            modifier = Modifier
                .width(310.dp),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            placeholder = { Text("Password") },
            visualTransformation =  PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .width(310.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password Input
        TextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("IP Address") },
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Text
            ),
            modifier = Modifier
                .width(310.dp),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password Input
        TextField(
            value = accessToken,
            singleLine = true,
            onValueChange = { accessToken = it },
            label = { Text("Access Token") },
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Text
            ),
            modifier = Modifier
                .width(310.dp)
        )
        // Get the lifecycle scope
        val lifecycleScope = rememberCoroutineScope()

        Spacer(modifier = Modifier.height(16.dp))
        // Login Button
        Button(
            onClick = {
                // Handle login button click
                lifecycleScope.launch {
                    onLoginClick(username, password, ip, accessToken, navController, reactiveState, applicationContext)
                }
            },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(darkPurple)),
            modifier = Modifier
                .height(50.dp)
        ) {
            Text("Log In")
        }
    }
}

fun initiateAppColors(applicationContext: Context) {
    // App colors
    white = ContextCompat.getColor(applicationContext, R.color.text_white)
    darkGrey = ContextCompat.getColor(applicationContext, R.color.default_grey)
    purpleGrey = ContextCompat.getColor(applicationContext, R.color.purple_grey)
    lightPurple = ContextCompat.getColor(applicationContext, R.color.light_purple)
    grey = ContextCompat.getColor(applicationContext, R.color.grey)
    darkPurple = ContextCompat.getColor(applicationContext, R.color.dark_purple)
}


suspend fun onLoginClick(
    username: String,
    password: String,
    ipInitial: String,
    accessToken: String,
    navController: NavHostController,
    reactiveState: ReactiveState,
    applicationContext: Context
) {

    val sharedPrefs = reactiveState.sharedPrefs

    val jellyfin = createJellyfin {
        clientInfo = ClientInfo(name = "My awesome client!", version = "1.33.7",)
        context = applicationContext
    }

    // Ip validation
    var ip = ipInitial.trim()

    // command into terminal "adb reverse tcp:8096 tcp:8096" to connect localhost port to phone
    // change to "10.0.2.2" when using computer emulator

    var ipDev = "localhost"
    ip = if (ipInitial == "") ipDev else ipInitial
    ip = if (ip.endsWith(":8096")) ip else "$ip:8096"
    ip = if (ip.startsWith("http://")) ip else "http://$ip"

    val api = jellyfin.createApi(
        baseUrl = ip,
        accessToken = accessToken.trim(),
    )

    try {
            val authenticationResult by api.userApi.authenticateUserByName(AuthenticateUserByName(username.trim(), password.trim()))

            // Use access token in api instance
            api.accessToken = authenticationResult.accessToken
            apiKey = accessToken.trim()

            // Save data (super not safe probably)
            sharedPrefs.edit()
                .putBoolean("loggedIn", true)
                .putString("username", username)
                .putString("password", password)
                .putString("ip", ip)
                .putString("accessToken", api.accessToken.toString())
            .apply()
            // Open Home activity
            navController.navigate(NavGraph.Home.route)


    } catch (err: Exception) {
        Log.d("LoginActivity", Log.getStackTraceString(err))
    }

}


