package com.example.vprint

import android.R.attr.port
import android.content.ContentValues.TAG
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.*
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getSystemService
import com.example.vprint.ui.theme.VPrintTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

class MainActivity : ComponentActivity() {
    var SERVICE_TYPE = "_ipp._tcp."
    var SERVICE_NAME = "vPrint"
    var mServiceName = ""
    var mDeviceName: String = "User"
    var mNsdManager: NsdManager? = null
    var mRegistrationListener: RegistrationListener? = null
    var mDiscoveryListener: DiscoveryListener? = null
    var mResolveListener: ResolveListener? = null
    private val logState = mutableStateOf(listOf<String>())

    fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            // The name is subject to change based on conflicts
            // with other services advertised on the same network.
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        mNsdManager?.registerService(serviceInfo, PROTOCOL_DNS_SD, mRegistrationListener)
    }

    fun initialRegistrationListener() {
        mRegistrationListener = object : RegistrationListener {

            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                // Save the service name. Android may have changed it in order to
                // resolve a conflict, so update the name you initially requested
                // with the name Android actually used.
                mServiceName = NsdServiceInfo.serviceName
                Log.d(TAG, "Service registered: $mServiceName")
                logMessage("Service registered: $mServiceName")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Registration failed! Put debugging code here to determine why.
                Log.d(TAG, "Service registration failed: $errorCode")
                logMessage("Service registration failed: $errorCode", isError = true)
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                // Service has been unregistered. This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.
                Log.d(TAG, "Service unregistered: " + arg0.serviceName)
                logMessage("Service unregistered: ${arg0.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Unregistration failed. Put debugging code here to determine why.
                Log.d(TAG, "Service unregistration failed: $errorCode")
                logMessage("Service unregistration failed: $errorCode", isError = true)
            }
        }
    }

    fun initializeDiscoveryListener() {
        mDiscoveryListener = object : DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started")
                logMessage("Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service discovery success $service")
                logMessage("Service found: $service")
                if (service.serviceType != SERVICE_TYPE) {
//                  mNsdManager?.resolveService(service, mResolveListener)
                    Log.d(TAG, "Unknown Service Type: " + service.serviceType)
                    Log.d(TAG, "Service Type: " + SERVICE_TYPE)
                    logMessage("Unknown Service Type: ${service.serviceType}", isError = true)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e(TAG, "Service lost $service")
                logMessage("Service lost: $service", isError = true)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped: $serviceType")
                logMessage("Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
                logMessage("Discovery failed: Error code: $errorCode", isError = true)
                mNsdManager?.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
                logMessage("Stop discovery failed: Error code: $errorCode", isError = true)
                mNsdManager?.stopServiceDiscovery(this)
            }
        }
    }

    fun discoverServices() {

        mNsdManager?.discoverServices(SERVICE_TYPE, PROTOCOL_DNS_SD, mDiscoveryListener)
    }

    fun initializeResolveListener() {
        mResolveListener = object : ResolveListener {

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Called when the resolve fails. Use the error code to debug.
                Log.e(TAG, "Resolve failed: $errorCode")
                logMessage("Resolve failed: $errorCode", isError = true)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.e(TAG, "Resolve Succeeded. $serviceInfo")
                logMessage("Resolve Succeeded: ${serviceInfo.host}:${serviceInfo.port}")
                Log.d(TAG, "Resolved service: ${serviceInfo.host}:${serviceInfo.port}")
                val port: Int = serviceInfo.port
                val host: InetAddress = serviceInfo.host
//                    mService = serviceInfo
                if (serviceInfo.serviceName == mServiceName) {
                    Log.d(TAG, "Same IP.")
                    logMessage("Same IP detected, ignoring")
                    return
                }
            }
        }

    }

    fun initializeServerSocket() {
        try {
            // Temporary socket just to get available port
            val tempSocket = ServerSocket(0).apply {
//                close() // Close immediately after getting port
                Log.d(TAG, "Advertising service on port: ${this.localPort}")
                logMessage("Advertising service on port: ${this.localPort}")
                registerService(this.localPort) // Register with obtained port
            }


        } catch (e: IOException) {
            Log.e(TAG, "Port registration failed: ${e.message}")
            logMessage("Port registration failed: ${e.message}", isError = true)
        }
    }

    private fun tearDown() {
        mNsdManager?.apply {
            mDiscoveryListener?.let { stopServiceDiscovery(it) }
            mRegistrationListener?.let { unregisterService(it) }
        }
    }

    private fun logMessage(message: String, isError: Boolean = false) {
        if (isError) {
            Log.e("VPRINT", message)
            logState.value = logState.value + "❌ ERROR: $message"
        } else {
            Log.d("VPRINT", message)
            logState.value = logState.value + message
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mNsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        initialRegistrationListener()
        initializeResolveListener()

        initializeServerSocket()  // Get port first
        initializeDiscoveryListener()
        CoroutineScope(Dispatchers.IO).launch {
            delay(1000)
            discoverServices()        // Start discovery
        }

        setContent {
            VPrintTheme {
                MainScreen(logState)
            }

        }
    }
}

@Composable
fun LogViewer(logs: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        logs.forEach { log ->
            Text(
                text = log,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun MainScreen(logState: MutableState<List<String>>) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        LogViewer(logs = logState.value, modifier = Modifier.padding(innerPadding))
    }
}