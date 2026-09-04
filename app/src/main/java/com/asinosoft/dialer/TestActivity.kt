package com.asinosoft.dialer

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.asinosoft.dialer.data.model.CallState
import com.asinosoft.dialer.service.TestService
import com.asinosoft.dialer.ui.theme.DialerTheme

class TestActivity : ComponentActivity() {
    var service: TestService? = null
    val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance.
            val binder = binder as TestService.LocalBinder
            service = binder.getService()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Intent(this, TestService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }

        setContent {
            DialerTheme {
                NotificationMenu()
            }
        }
    }
}

@Composable
private fun TestActivity.NotificationMenu() {
    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Button(
            onClick = {
                service?.showCallNotification(
                    CallState(
                        state = Call.STATE_RINGING,
                        rawNumber = "79137106193",
                        displayName = "Birdie",
                        connectTimeMillis = 1234,
                        simNumber = 2
                    )
                )
            },
        ) {
            Text("Incoming")
        }
        Button(
            onClick = {
                service?.showCallNotification(
                    CallState(
                        state = Call.STATE_ACTIVE,
                        rawNumber = "79137106193",
                        displayName = "Recipient",
                        connectTimeMillis = 1234,
                        simNumber = 2
                    )
                )
            },
        ) {
            Text("Ongoing")
        }
        Button(
            onClick = {
                service?.showMissedCallNotification(
                    rawNumber = "79529161612"
                )
            },
        ) {
            Text("Missed")
        }
        Button(
            onClick = {
                service?.hideNotification()
            },
        ) {
            Text("Hide")
        }
    }
}
