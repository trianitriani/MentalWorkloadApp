package com.example.mentalworkloadapp.ui.theme

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import mylibrary.mindrove.ServerManager
import mylibrary.mindrove.SensorData
// import mylibrary.mindrove.Instruction // if needed for instructions

class MainActivityHistory : ComponentActivity() {

    private val sensorDataDisplay = MutableLiveData("No data yet. Ensure headset is connected via Wi-Fi.")      // LiveData to hold sensor data text for the UI
    private val networkStatusDisplay = MutableLiveData("Checking network status...")        // LiveData for network status

    // Instantiate ServerManager with a callback for handling new data
    private val serverManager = ServerManager { sensorData: SensorData ->
        // This block is called when new data arrives from the headset
        val dataString = """
            EEG Ch1: ${sensorData.channel1} µV 
            EEG Ch2: ${sensorData.channel2} µV
            EEG Ch3: ${sensorData.channel3} µV
            EEG Ch4: ${sensorData.channel4} µV
            EEG Ch5: ${sensorData.channel5} µV
            EEG Ch6: ${sensorData.channel6} µV
            EEG Ch7: ${sensorData.channel7} µV
            EEG Ch8: ${sensorData.channel8} µV
            Accel X: ${sensorData.accelerationX}
            Accel Y: ${sensorData.accelerationY}
            Accel Z: ${sensorData.accelerationZ}
            Gyro X: ${sensorData.angularRateX}
            Gyro Y: ${sensorData.angularRateY}
            Gyro Z: ${sensorData.angularRateZ}
            Battery: ${sensorData.voltage}%
            Packet ID: ${sensorData.numberOfMeasurement}
        """.trimIndent()

        Log.d("MindRoveData", dataString)
        sensorDataDisplay.postValue(dataString)
    }

    private lateinit var networkCheckHandler: Handler
    private lateinit var networkCheckRunnable: Runnable
    private var isServerManagerActive = false
    private var isWifiSettingsScreenOpen = false

    private val wifiSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // User has returned from Wi-Fi settings
            isWifiSettingsScreenOpen = false
            // The runnable will re-check the network status shortly
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        networkCheckHandler = Handler(Looper.getMainLooper())
        networkCheckRunnable = Runnable {
            checkNetworkAndManageServer()
            // repeat this check periodically
            networkCheckHandler.postDelayed(networkCheckRunnable, 5000)
        }

        // Start the periodic network check
        networkCheckHandler.post(networkCheckRunnable)

        setContent {
            MentalWorkloadAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentNetworkStatus by networkStatusDisplay.observeAsState("")
                    val currentSensorData by sensorDataDisplay.observeAsState("")
                    MindRoveAppScreen(
                        networkStatus = currentNetworkStatus,
                        sensorData = currentSensorData
                    )
                }
            }
        }
    }

    private fun checkNetworkAndManageServer() {
        if (!isNetworkConnected()) {
            networkStatusDisplay.value = "No network. Please connect to MindRove headset Wi-Fi."
            if (!isWifiSettingsScreenOpen) { // Prevent opening settings multiple times
                promptToOpenWifiSettings()
                isWifiSettingsScreenOpen = true
            }
        } else {
            networkStatusDisplay.value = "Network connected. Waiting for headset data..."
            isWifiSettingsScreenOpen = false // Reset flag as network is now connected

            if (!isServerManagerActive) {
                Log.d("MindRoveSetup", "Network available, starting ServerManager.")
                serverManager.start() // Start the server to listen for data
                isServerManagerActive = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCheckHandler.removeCallbacks(networkCheckRunnable) // Stop network checks
        if (isServerManagerActive) {
            serverManager.stop() // Stop the server and clean up resources
            isServerManagerActive = false
            Log.d("MindRoveSetup", "ServerManager stopped.")
        }
    }

    private fun isNetworkConnected(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) // MindRove uses Wi-Fi
    }

    private fun promptToOpenWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        if (intent.resolveActivity(packageManager) != null) {
            wifiSettingsLauncher.launch(intent)
        } else {
            Log.e("MindRoveSetup", "Cannot open Wi-Fi settings, no activity found to handle intent.")
        }
    }
}

@Composable
fun MindRoveAppScreen(
    networkStatus: String,
    sensorData: String,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "MindRove EEG Data Collector",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Network: $networkStatus",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Sensor Data:",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = sensorData,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MindRoveAppScreenPreview() {
    MentalWorkloadAppTheme {
        MindRoveAppScreen(
            networkStatus = "Network: Connected. Waiting for headset data...",
            sensorData = "EEG Ch1: 123.45 µV\nAccel X: 10\nBattery: 80%"
        )
    }
}