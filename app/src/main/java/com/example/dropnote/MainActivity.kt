package com.example.dropnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var running by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("DropNote", style = MaterialTheme.typography.headlineLarge)

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            if (running) "Server is running on port 8080"
                            else "Server is stopped"
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (running) {
                                    DropNoteServer.stop()
                                } else {
                                    DropNoteServer.start(this@MainActivity)
                                }

                                running = !running
                            }
                        ) {
                            Text(if (running) "Stop Server" else "Start Server")
                        }
                    }
                }
            }
        }
    }
}