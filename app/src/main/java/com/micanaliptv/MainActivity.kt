package com.micanaliptv

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Leemos credenciales guardadas previamente en la TV Box
        val prefs = getSharedPreferences("MiCanalPrefs", Context.MODE_PRIVATE)
        val savedServer = prefs.getString("server", "http://160.238.185.88:25461") ?: "http://160.238.185.88:25461"
        val savedUser = prefs.getString("username", "") ?: ""
        val savedPass = prefs.getString("password", "") ?: ""
        val savedStreamId = prefs.getString("streamId", "1") ?: "1"

        setContent {
            var isLoggedIn by remember { mutableStateOf(savedUser.isNotEmpty() && savedPass.isNotEmpty()) }
            var serverHost by remember { mutableStateOf(savedServer) }
            var username by remember { mutableStateOf(savedUser) }
            var password by remember { mutableStateOf(savedPass) }
            var streamId by remember { mutableStateOf(savedStreamId) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (!isLoggedIn) {
                    LoginScreen(
                        initialServer = serverHost,
                        initialUser = username,
                        initialPass = password,
                        initialStream = streamId,
                        onLogin = { s, u, p, st ->
                            // Guardamos los datos permanentemente en la TV Box
                            prefs.edit()
                                .putString("server", s)
                                .putString("username", u)
                                .putString("password", p)
                                .putString("streamId", st)
                                .apply()

                            serverHost = s
                            username = u
                            password = p
                            streamId = st
                            isLoggedIn = true
                        }
                    )
                } else {
                    val videoUrl = "$serverHost/live/$username/$password/$streamId.ts"
                    VideoPlayerScreen(
                        videoUrl = videoUrl,
                        onChangeUser = {
                            // Limpiamos memoria y volvemos al login
                            prefs.edit().clear().apply()
                            isLoggedIn = false
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun LoginScreen(
        initialServer: String,
        initialUser: String,
        initialPass: String,
        initialStream: String,
        onLogin: (String, String, String, String) -> Unit
    ) {
        var server by remember { mutableStateOf(initialServer) }
        var user by remember { mutableStateOf(initialUser) }
        var pass by remember { mutableStateOf(initialPass) }
        var stream by remember { mutableStateOf(initialStream) }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(450.dp).padding(16.dp),
                color = Color.DarkGray,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "MiCanalTV - Configuración",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )

                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = { Text("URL Servidor", color = Color.LightGray) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("Usuario", color = Color.LightGray) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Contraseña", color = Color.LightGray) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = stream,
                        onValueChange = { stream = it },
                        label = { Text("ID de Canal (Ej: 1)", color = Color.LightGray) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { 
                            if (user.isNotEmpty() && pass.isNotEmpty()) {
                                onLogin(server, user, pass, stream)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Guardar y Conectar")
                    }
                }
            }
        }
    }

    @Composable
    fun VideoPlayerScreen(videoUrl: String, onChangeUser: () -> Unit) {
        val context = androidx.compose.ui.platform.LocalContext.current
        
        val player = remember {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(videoUrl)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
        }

        exoPlayer = player

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Botón en la esquina superior para cambiar de usuario en cualquier momento
            Button(
                onClick = {
                    player.release()
                    onChangeUser()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
            ) {
                Text("Cambiar Usuario")
            }
        }
    }

    override fun onStop() {
        super.onStop()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}
