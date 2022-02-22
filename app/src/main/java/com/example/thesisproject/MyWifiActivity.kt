package com.example.thesisproject

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.*
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.View.INVISIBLE
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.koushikdutta.ion.Ion
import java.net.*
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

class MyWifiActivity : AppCompatActivity() {
    private lateinit var deviceList: ListView
    val FINE_LOCATION_RQ = 101
    val p2pManager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }
    lateinit var wifiManager: WifiManager
    var channel: WifiP2pManager.Channel? = null
    var receiver: BroadcastReceiver? = null
    val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }
    val peers = ArrayList<WifiP2pDevice>()
    private lateinit var statusText: TextView
    lateinit var message: TextView
    lateinit var messageText: EditText
    lateinit var sendBtn: Button
    lateinit var p2pBtn: Button
    lateinit var hotspotBtn: Button
    lateinit var hotspotConnectBtn: Button
    lateinit var hotspotReservation: WifiManager.LocalOnlyHotspotReservation
    lateinit var peerListListener: WifiP2pManager.PeerListListener
    lateinit var connectionInfoListener: WifiP2pManager.ConnectionInfoListener
    var socket: Socket? = null
    lateinit var server: Server
    lateinit var client: Client
    lateinit var udpServer: UdpServerClient
    var isHost = false
    val rangeArray = ArrayList<Marzullo.Range>()
    var offset = 0.0
    var udp = true
    lateinit var hostAddress: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        message = findViewById(R.id.message)
        messageText = findViewById(R.id.messageText)
        deviceList = findViewById(R.id.deviceList)
        p2pBtn = findViewById(R.id.p2pBtn)
        hotspotBtn = findViewById(R.id.hotspotBtn)
        hotspotConnectBtn = findViewById(R.id.hotspotConnectBtn)
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

        channel = p2pManager?.initialize(this, mainLooper, null)
        channel?.also { channel ->
            receiver = WiFiDirectBroadcastReceiver(p2pManager!!, channel, this)
        }
    }

    fun tcpOnlyClick(view: View) {
        val theBtn = view as Button
        if (theBtn.text.equals(getString(R.string.udpOff))) {
            udp = false
            theBtn.text = getString(R.string.udpOn)
        } else {
            udp = true
            theBtn.text = getString(R.string.udpOff)
        }
    }

    fun discoverDevices() {
        peerListListener = WifiP2pManager.PeerListListener {
            if (it.deviceList != peers) {
                peers.clear()
                peers.addAll(it.deviceList)
                val adapter = ArrayAdapter<String>(
                    applicationContext,
                    android.R.layout.simple_list_item_1,
                    Array<String>(peers.size) { i -> peers[i].deviceName })
                deviceList.adapter = adapter
            }
        }
    }

    fun discoverPeers() {
        /* Discover peers */
        checkPermission(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            "location",
            FINE_LOCATION_RQ
        )
        p2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                statusText.text = "Discovery started"
            }

            override fun onFailure(reason: Int) {
                statusText.text = "Discovery failed to start, reason: $reason"
                Log.d("Failed", "Reason for failure: $reason")
            }
        })
    }

    fun listenForInfo() {
        connectionInfoListener = WifiP2pManager.ConnectionInfoListener {
            val groupOwnerAddress = it.groupOwnerAddress

            if (it.groupFormed) {
                if (it.isGroupOwner) {
                    statusText.text = "P2P Host"
                    if (udp) {
                        udpServer = UdpServerClient(this)
                        udpServer.start()
                    } else {
                        isHost = true
                        server = Server(this)
                        server.start()
                    }
                } else {
                    statusText.text = "P2P Client"
                    hostAddress = groupOwnerAddress.hostAddress
                    if (!udp) {
                        isHost = false
                        client = Client(groupOwnerAddress, this)
                        client.start()
                    }
                }
                //p2pBtn.text = getString(R.string.closeP2p)
                stopP2pDiscovery()
            }
        }
    }

    fun onP2pClick(view: View) {
        //if(p2pBtn.text.equals(getString(R.string.p2p))) {
        discoverDevices()
        discoverPeers()
        //}
        /*else{
            p2pManager!!.removeGroup(channel, object : WifiP2pManager.ActionListener{
                override fun onSuccess() {
                    socket?.close()
                    p2pBtn.text = getString(R.string.p2p)
                }

                override fun onFailure(reason: Int) {
                    Toast.makeText(this@MyWifiActivity, "Could not close P2P Connection", Toast.LENGTH_SHORT).show()
                }

            })
        }*/
    }

    @RequiresApi(30)
    fun onHotspotClick(view: View) {
        val builder = AlertDialog.Builder(this)
        builder.apply {
            setTitle("Hotspot or Local WiFi")
            setNegativeButton("Hotspot") { _, _ ->
                checkPermission(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    "location",
                    FINE_LOCATION_RQ
                )
                wifiManager.startLocalOnlyHotspot(object :
                    WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                        super.onStarted(reservation)
                        hotspotReservation = reservation!!
                        if (!udp) {
                            server = Server(this@MyWifiActivity)
                            server.start()
                        } else {
                            udpServer = UdpServerClient(this@MyWifiActivity)
                            udpServer.start()
                        }
                        statusText.text = getString(
                            R.string.hotspotInfo,
                            hotspotReservation.softApConfiguration.ssid,
                            hotspotReservation.softApConfiguration.passphrase,
                            ipAddress()
                        )
                        hotspotBtn.visibility = INVISIBLE
                    }

                }, null)
            }
            setPositiveButton("Local WiFi") { _, _ ->
                Ion.with(this@MyWifiActivity)
                    .load("https://api.ipify.org?format=json")
                    .asJsonObject()
                    .setCallback { e, result ->
                        statusText.text =
                            getString(R.string.localInfo, ipAddress(), result.get("ip").asString)
                    }
                if (udp) {
                    udpServer = UdpServerClient(this@MyWifiActivity)
                    udpServer.start()
                } else {
                    server = Server(this@MyWifiActivity)
                    server.start()
                }
                hotspotBtn.visibility = INVISIBLE
            }
        }

        val dialog = builder.create()
        dialog.show()
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        )
        val layoutParams2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        )
        layoutParams.rightMargin = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            50f, resources.displayMetrics
        ).toInt()
        layoutParams2.rightMargin = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            20f, resources.displayMetrics
        ).toInt()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).layoutParams = layoutParams
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).layoutParams = layoutParams2

    }

    @RequiresApi(30)
    fun onHotspot4gConnectClick(view: View) {
        if (hotspotConnectBtn.text.equals(getString(R.string.connect))) {
            val ssid = EditText(this)
            val password = EditText(this)
            val hostIp = EditText(this)
            hostIp.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hostIp.keyListener = DigitsKeyListener.getInstance("0123456789.")
            hostIp.hint = "Host IP Address"
            ssid.text = Editable.Factory.getInstance().newEditable("AndroidShare_")
            ssid.inputType = InputType.TYPE_CLASS_TEXT
            password.hint = "Password"
            //password.transformationMethod = PasswordTransformationMethod.getInstance()
            val layout = LinearLayout(this)
            layout.orientation = LinearLayout.VERTICAL
            layout.addView(ssid)
            layout.addView(password)
            layout.addView(hostIp)
            val builder = AlertDialog.Builder(this)
            builder.apply {
                setTitle("Connect to a server")
                setMessage(
                    "Enter SSID & Password for hotspot or leave empty to connect " +
                            "over Local WiFi/Internet. Host IP is required"
                )
                setView(layout)
                setPositiveButton("Connect") { _, _ ->
                    if (hostIp.text.toString().isBlank()) {
                        Toast.makeText(
                            this@MyWifiActivity,
                            "Host IP is required",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    } else {
                        hostAddress = hostIp.text.toString()
                        connectToHotspotOrLocal(ssid.text.toString(), password.text.toString())
                        if (!udp) hotspotConnectBtn.text = getString(R.string.disconnect)
                    }
                }
                setNegativeButton("Cancel") { _, _ -> }
            }
            val dialog = builder.create()
            dialog.show()
        } else {
            try {
                socket?.close()
            } catch (e: SocketException) {
                Toast.makeText(this, "Could not close socket", Toast.LENGTH_SHORT).show()
            }
            hotspotConnectBtn.text = getString(R.string.connect)
            if (this::client.isInitialized) statusText.text = ""
        }
    }

    @RequiresApi(29)
    private fun connectToHotspotOrLocal(ssid: String, password: String) {
        if (password.isEmpty()) {
            if (!udp) {
                client = Client(InetAddress.getByName(hostAddress), this@MyWifiActivity)
                client.start()
            }
            statusText.text = "Client"
        } else {
            val config = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password).build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(config).build()

            val connectivityManager =
                applicationContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                @SuppressLint("DefaultLocale")
                override fun onAvailable(network: Network) {
                    Toast.makeText(this@MyWifiActivity, "Connected to $ssid", Toast.LENGTH_SHORT)
                        .show()
                    println("There is an active data connection: ${connectivityManager.isDefaultNetworkActive}")
                    connectivityManager.bindProcessToNetwork(null)
                    connectivityManager.bindProcessToNetwork(network)
                    if (!udp) {
                        client = Client(InetAddress.getByName(hostAddress), this@MyWifiActivity)
                        client.start()
                    }
                    val handler = Handler(Looper.getMainLooper())
                    handler.post { statusText.text = "Hotspot Client" }
                }

            }
            connectivityManager.requestNetwork(request, networkCallback)
        }
    }

    override fun onResume() {
        super.onResume()
        receiver?.also { receiver ->
            registerReceiver(receiver, intentFilter)
        }
        onDeviceClick()
        listenForInfo()
    }

    override fun onPause() {
        super.onPause()
        receiver?.also { receiver ->
            unregisterReceiver(receiver)
        }
    }

    fun onDeviceClick() {
        deviceList.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                val device = peers[position]
                val config = WifiP2pConfig()
                config.deviceAddress = device.deviceAddress

                checkPermission(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    "location",
                    FINE_LOCATION_RQ
                )
                p2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Toast.makeText(
                            applicationContext,
                            "Connected to ${device.deviceName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    override fun onFailure(reason: Int) {
                        Toast.makeText(
                            applicationContext,
                            "Could not connect to ${device.deviceName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                })
            }
    }

    fun sendOnClick(view: View) {
        if (statusText.text.contains("Client")) {
            val radioGroup = RadioGroup(this)
            val ten = RadioButton(this)
            ten.text = "10"
            val thirty = RadioButton(this)
            thirty.text = "30"
            val hundred = RadioButton(this)
            hundred.text = "100"
            radioGroup.addView(ten)
            radioGroup.addView(thirty)
            radioGroup.addView(hundred)
            radioGroup.check(ten.id)
            val builder = AlertDialog.Builder(this)
            builder.apply {
                setTitle("Number of requests")
                setMessage("Choose the number of tests to perform")
                setView(radioGroup)
                setPositiveButton("OK"){_, _ ->
                    val id = radioGroup.checkedRadioButtonId
                    val choice = if(id==ten.id) 10 else if(id==thirty.id) 30 else 100
                    val executor = Executors.newSingleThreadExecutor() as ExecutorService
                    val handler = Handler(Looper.getMainLooper())
                    executor.execute {
                        if (udp) {
                            for (i in 0 until choice) {
                                val range = UdpServerClient.clientSendReceive(
                                    InetAddress.getByName(hostAddress),
                                    "Requesting time"
                                )
                                println(
                                    "Range start: ${range.start}" +
                                            "\nRange end: ${range.end}\nOffset: ${(range.end + range.start.toDouble()) / 2}"
                                )
                                rangeArray.add(range)
                                handler.post {
                                    message.text = "Range start: ${range.start}" +
                                            "\nRange end: ${range.end}\nOffset: ${(range.end + range.start.toDouble()) / 2}"
                                }
                                Thread.sleep(100)
                            }
                        } else {
                            for (i in 0 until choice) {
                                client.write("Requesting time")
                                Thread.sleep(200)
                            }
                        }
                    }
                }
                setNegativeButton("Cancel"){_, _ ->}
            }
            val dialog = builder.create()
            dialog.show()
        }
    }

    fun offsetOnClick(view: View?) {
        println("Arrray size: ${rangeArray.size}")
        val marzullo = Marzullo.apply(rangeArray)
        offset = (marzullo.start + marzullo.end.toDouble()) / 2

        findViewById<TextView>(R.id.avgDelay).text =
            "Marzullo range: [${marzullo.start}, ${marzullo.end}]" +
                    "\nOffset: $offset"
        rangeArray.clear()
    }

    fun checkPermission(permission: String, name: String, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    permission
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Toast.makeText(
                        applicationContext,
                        "$name permission granted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                shouldShowRequestPermissionRationale(permission) -> showDialog(
                    permission,
                    name,
                    requestCode
                )

                else -> ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        fun innerCheck(name: String) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(applicationContext, "$name permission rejected", Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(applicationContext, "$name permission granted", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        when (requestCode) {
            FINE_LOCATION_RQ -> innerCheck("location")
        }
    }

    private fun showDialog(permission: String, name: String, requestCode: Int) {
        val builder = AlertDialog.Builder(this)
        builder.apply {
            setMessage("Permission to access your $name is required to use this app")
            setTitle("Permission Required")
            setPositiveButton("OK") { dialog, which ->
                ActivityCompat.requestPermissions(
                    this@MyWifiActivity,
                    arrayOf(permission),
                    requestCode
                )
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun stopP2pDiscovery() {
        p2pManager!!.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}

            override fun onFailure(reason: Int) {}
        })
    }

    /** from stack overflow **/
    private fun ipAddress(): String? {
        var ip = ""
        try {
            val enumNetworkInterfaces = NetworkInterface
                .getNetworkInterfaces()
            while (enumNetworkInterfaces.hasMoreElements()) {
                val networkInterface = enumNetworkInterfaces
                    .nextElement()
                val enumInetAddress = networkInterface
                    .inetAddresses
                while (enumInetAddress.hasMoreElements()) {
                    val inetAddress = enumInetAddress.nextElement()
                    if (inetAddress.isSiteLocalAddress) {
                        ip = inetAddress.hostAddress
                    }
                }
            }
        } catch (e: SocketException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
            ip += """
            Something Wrong! $e
            
            """.trimIndent()
        }
        return ip
    }
}