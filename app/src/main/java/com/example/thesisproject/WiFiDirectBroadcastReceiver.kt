package com.example.thesisproject

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.widget.Toast
import androidx.core.app.ActivityCompat

class WiFiDirectBroadcastReceiver(
        private val manager: WifiP2pManager,
        private val channel: WifiP2pManager.Channel,
        private val activity: MyWifiActivity
        ) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        when(action){
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                //Check to see if wifi is enabled and notify appropriate activity
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                when(state){
                    WifiP2pManager.WIFI_P2P_STATE_ENABLED -> {
                        //Wifi P2P is enabled
                        Toast.makeText(context, "Wifi is on", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(context, "Wifi is off", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                //Call manager.requestPeers() to get a list of current peers
                activity.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, "location", activity.FINE_LOCATION_RQ)
                manager.requestPeers(channel,activity.peerListListener)
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                //Respond to a new connection or disconnection
                //how to check if devices are connected? NetworkInfo is deprecated
                manager.requestConnectionInfo(channel, activity.connectionInfoListener)
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                // Respond to this device's wifi state changing
            }
        }
    }
}