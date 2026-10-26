package com.micanaliptv

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class LiveStream(
    val num: Int = 0,
    val name: String = "",
    val stream_id: Long = 0,
    val stream_icon: String = ""
)

data class UserInfo(
    val auth: Int,
    val status: String?
)

data class LoginResponse(
    val user_info: UserInfo?
)

class MainActivity : ComponentActivity() {

    private val DEFAULT_SERVER = "http://160.238.185.88:25461"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = localContextOrNull() ?: this
            val prefs = remember { context.getSharedPreferences("MiCanalPrefs", Context.MODE_PRIVATE) }
            
            // Guardamos la URL por defecto internamente si no existe
            val savedServer = prefs.getString("server", DEFAULT_SERVER) ?: DEFAULT_SERVER
            val savedUser = prefs.getString("username", "") ?: ""
            val savedPass = prefs.getString("password", "") ?: ""

            var isLoggedIn by remember { mutableStateOf(savedUser.isNotEmpty() && savedPass.isNotEmpty()) }
            var serverHost by remember { mutableStateOf(savedServer) }
            var username by remember { mutableStateOf(savedUser) }
            var password by remember { mutableStateOf(savedPass) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (!isLoggedIn) {
                    LoginScreen(
                        initialUser = username,
                        initialPass = password,
                        onLoginSuccess = { u, p ->
                            prefs.edit()
                                .putString("server", DEFAULT_SERVER)
                                .putString("username", u)
                                .putString("password", p)
                                .apply()

                            serverHost = DEFAULT_SERVER
                            username = u
                            password = p
                            isLoggedIn = true
                        }
                    )
                } else {
                    TVPlayerScreen(
                        serverHost = serverHost,
                        username = username,
                        password = password,
                        onChangeUser = {
                            prefs.edit().clear().apply()
                            isLoggedIn = false
                        }
                    )
                }
            }
        }
    }

    private fun localContextOrNull(): Context? = this

    @Composable
    fun LoginScreen(
        initialUser: String,
        initialPass: String,
        onLoginSuccess: (String, String) -> Unit
    ) {
        var user by remember { mutableStateOf(initialUser) }
        var pass by remember { mutableStateOf(initialPass) }
        var errorMessage by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        val loginFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            try {
                loginFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(440.dp).padding(16.dp),
                color = Color.DarkGray,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val logoRes = remember {
                        try { R.drawable.logo } catch (e: Exception) { 0 }
                    }

                    if (logoRes != 0) {
                        Image(
                            painter = painterResource(id = logoRes),
                            contentDescription = "Logo",
                            modifier = Modifier.height(70.dp).fillMaxWidth()
                        )
                    }

                    Text(
                        text = "MiCanalTV - Acceso",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )

                    // URL oculta al usuario (queda fija por código)

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

                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = {
                            if (user.isNotEmpty() && pass.isNotEmpty()) {
                                isLoading = true
                                errorMessage = ""
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val cleanServer = DEFAULT_SERVER.trim().removeSuffix("/")
                                        val apiUrl = "$cleanServer/player_api.php?username=$user&password=$pass"
                                        
                                        val url = URL(apiUrl)
                                        val connection = url.openConnection() as HttpURLConnection
                                        connection.connectTimeout = 8000
                                        connection.readTimeout = 8000
                                        connection.instanceFollowRedirects = true

                                        if (connection.responseCode == 200) {
                                            val json = connection.inputStream.bufferedReader().readText()
                                            if (json.trim().startsWith("{")) {
                                                val loginResp = Gson().fromJson(json, LoginResponse::class.java)
                                                if (loginResp?.user_info?.auth == 1) {
                                                    withContext(Dispatchers.Main) {
                                                        isLoading = false
                                                        onLoginSuccess(user, pass)
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        isLoading = false
                                                        errorMessage = "Usuario o contraseña incorrectos"
                                                    }
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    isLoading = false
                                                    errorMessage = "Respuesta inválida del servidor"
                                                }
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                isLoading = false
                                                errorMessage = "Error del servidor (HTTP ${connection.responseCode})"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            errorMessage = "Fallo de red: verifique conexión"
                                        }
                                    }
                                }
                            } else {
                                errorMessage = "Complete usuario y contraseña"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .focusRequester(loginFocusRequester),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text("Iniciar Sesión")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun TVPlayerScreen(
        serverHost: String,
        username: String,
        password: String,
        onChangeUser: () -> Unit
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val prefs = remember { context.getSharedPreferences("MiCanalPrefs", Context.MODE_PRIVATE) }
        
        var channels by remember { mutableStateOf<List<LiveStream>>(emptyList()) }
        var currentChannelIndex by remember { mutableIntStateOf(0) }
        var isChannelListVisible by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(true) }
        var errorStatus by remember { mutableStateOf("") }
        
        // Zapping numérico
        var inputNumberString by remember { mutableStateOf("") }
        var showNumberBanner by remember { mutableStateOf(false) }
        val handler = remember { Handler(Looper.getMainLooper()) }

        val playerFocusRequester = remember { FocusRequester() }
        val coroutineScope = rememberCoroutineScope()
        val listState = rememberLazyListState()

        val mediaPlayer = remember { MediaPlayer() }

        // Cargar canales
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val cleanServer = serverHost.trim().removeSuffix("/")
                val cachedJson = prefs.getString("cached_channels", null)
                val gson = Gson()

                if (!cachedJson.isNullOrEmpty()) {
                    try {
                        val type = object : TypeToken<List<LiveStream>>() {}.type
                        val list: List<LiveStream> = gson.fromJson(cachedJson, type)
                        if (!list.isNullOrEmpty()) {
                            channels = list
                            isLoading = false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                try {
                    val apiUrl = "$cleanServer/player_api.php?username=$username&password=$password&action=get_live_streams"
                    val url = URL(apiUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000

                    if (connection.responseCode == 200) {
                        val json = connection.inputStream.bufferedReader().readText()
                        if (json.trim().startsWith("[")) {
                            val type = object : TypeToken<List<LiveStream>>() {}.type
                            val fetchedChannels: List<LiveStream> = gson.fromJson(json, type)
                            if (!fetchedChannels.isNullOrEmpty()) {
                                channels = fetchedChannels
                                prefs.edit().putString("cached_channels", json).apply()
                            }
                        } else {
                            errorStatus = "El servidor no devolvió canales"
                        }
                    } else {
                        errorStatus = "Error HTTP ${connection.responseCode}"
                    }
                } catch (e: Exception) {
                    errorStatus = "Error conectando al servidor"
                } finally {
                    isLoading = false
                }
            }
        }

        val currentChannel = channels.getOrNull(currentChannelIndex)
        val videoUrl = currentChannel?.let {
            "$serverHost/live/$username/$password/${it.stream_id}.ts"
        } ?: ""

        // Reproducir stream
        LaunchedEffect(videoUrl) {
            if (videoUrl.isNotEmpty()) {
                try {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(videoUrl)
                    mediaPlayer.prepareAsync()
                    mediaPlayer.setOnPreparedListener { mp ->
                        mp.start()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                try {
                    mediaPlayer.stop()
                    mediaPlayer.release()
                } catch (e: Exception) {}
            }
        }

        // Auto-scroll en la lista al cambiar el canal seleccionado
        LaunchedEffect(currentChannelIndex) {
            if (isChannelListVisible && channels.isNotEmpty()) {
                coroutineScope.launch {
                    listState.animateScrollToItem(currentChannelIndex)
                }
            }
        }

        LaunchedEffect(Unit) {
            playerFocusRequester.requestFocus()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(playerFocusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        val keyCode = keyEvent.nativeKeyEvent.keyCode

                        // Si la barra lateral de canales está abierta
                        if (isChannelListVisible) {
                            return@onKeyEvent when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (channels.isNotEmpty()) {
                                        currentChannelIndex = if (currentChannelIndex > 0) currentChannelIndex - 1 else channels.size - 1
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (channels.isNotEmpty()) {
                                        currentChannelIndex = if (currentChannelIndex < channels.size - 1) currentChannelIndex + 1 else 0
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                    isChannelListVisible = false
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                                    isChannelListVisible = false
                                    true
                                }
                                else -> false
                            }
                        }

                        // Zapping numérico
                        val numberPressed = when (keyCode) {
                            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> "0"
                            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> "1"
                            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> "2"
                            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> "3"
                            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> "4"
                            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> "5"
                            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> "6"
                            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> "7"
                            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> "8"
                            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> "9"
                            else -> null
                        }

                        if (numberPressed != null) {
                            inputNumberString += numberPressed
                            showNumberBanner = true
                            
                            handler.removeCallbacksAndMessages(null)
                            handler.postDelayed({
                                val targetNumber = inputNumberString.toIntOrNull()
                                if (targetNumber != null && targetNumber > 0 && targetNumber <= channels.size) {
                                    currentChannelIndex = targetNumber - 1
                                }
                                inputNumberString = ""
                                showNumberBanner = false
                            }, 2000)
                            return@onKeyEvent true
                        }

                        // Zapping con IZQUIERDA / DERECHA en pantalla completa (Evita conflictos con Arriba/Abajo)
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (channels.isNotEmpty()) {
                                    currentChannelIndex = if (currentChannelIndex > 0) currentChannelIndex - 1 else channels.size - 1
                                    true
                                } else false
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (channels.isNotEmpty()) {
                                    currentChannelIndex = if (currentChannelIndex < channels.size - 1) currentChannelIndex + 1 else 0
                                    true
                                } else false
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                isChannelListVisible = true
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            // Renderizado de video
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                mediaPlayer.setDisplay(holder)
                            }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                mediaPlayer.setDisplay(null)
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading && channels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            if (!isLoading && channels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        color = Color.DarkGray,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No se pudieron cargar los canales",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (errorStatus.isNotEmpty()) {
                                Text(
                                    text = errorStatus,
                                    color = Color.Red,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(onClick = {
                                try {
                                    mediaPlayer.stop()
                                    mediaPlayer.release()
                                } catch (e: Exception) {}
                                onChangeUser()
                            }) {
                                Text("Volver a Login")
                            }
                        }
                    }
                }
            }

            if (showNumberBanner) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(24.dp),
                    color = Color.Red.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Canal: $inputNumberString",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            } else if (currentChannel != null && !isChannelListVisible) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(24.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "CH ${currentChannelIndex + 1}: ${currentChannel.name}",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Menú lateral de canales operado por Arriba/Abajo sin conflictos
            if (isChannelListVisible) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(360.dp)
                        .align(Alignment.CenterStart)
                        .focusable(),
                    color = Color.Black.copy(alpha = 0.95f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Canales (${channels.size})",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Button(
                                onClick = {
                                    try {
                                        mediaPlayer.stop()
                                        mediaPlayer.release()
                                    } catch (e: Exception) {}
                                    onChangeUser()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Cerrar Sesión", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .focusable(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(channels) { index, channel ->
                                val isSelected = index == currentChannelIndex
                                
                                Button(
                                    onClick = {
                                        currentChannelIndex = index
                                        isChannelListVisible = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color.Red else Color.DarkGray,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(
                                        text = "${index + 1}. ${channel.name}",
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            finish()
        } catch (e: Exception) {}
    }
}
