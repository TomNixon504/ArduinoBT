package com.example.arduinobt

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.ViewModel
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayList


// Code to request to enable bluetooth
private const val REQUEST_ENABLE_BT = 1
// Standard SerialPortService ID
private const val serialPortServiceID = "00001101-0000-1000-8000-00805F9B34FB"
// Coded bytes for interpreting data sent and received
private const val SW1_CLOSED = 0x41
private const val SW1_OPEN = 0x45
private const val SW2_CLOSED = 0x42
private const val SW2_OPEN = 0x46
private const val REQUEST_LED1_ON = 0x4A
private const val CONFIRM_LED1_ON = 0x4B
private const val REQUEST_LED1_OFF = 0x4C
private const val CONFIRM_LED1_OFF = 0x4D
private const val REQUEST_LED2_ON = 0x4E
private const val CONFIRM_LED2_ON = 0x4F
private const val REQUEST_LED2_OFF = 0x50
private const val CONFIRM_LED2_OFF = 0x51
private const val REQUEST_PASS_ON = 0x01
private const val CONFIRM_PASS_ON = 0x02
private const val REQUEST_PASS_OFF = 0x04
private const val CONFIRM_PASS_OFF = 0x03

class MyBluetoothController : MainActivity(){

    private val viewModel: ItemViewModel by viewModels()

    // Booleans to know if the respective element is enabled or disabled
    private var sw1Enabled = false
    private var sw2Enabled = false
    private var led1Enabled = false
    private var led2Enabled = false
    private var passEnabled = false
    // Booleans to confirm if an action has been requested
    private var led1Requested = false
    private var led2Requested = false
    private var passRequested = false
    // The GUI elements that display the responses from the arduino
    private lateinit var sw1Image : ImageView
    private lateinit var sw2Image : ImageView
    private lateinit var led1Image : ImageView
    private lateinit var led2Image : ImageView
    private lateinit var passImage : ImageView
    // Bluetooth management
    private lateinit var mmDevice : BluetoothDevice
    private lateinit var mmSocket : BluetoothSocket
    private lateinit var mmOutputStream : OutputStream
    private lateinit var mmInputStream : InputStream
    private var stopWorker = false
    private var commsError = false
    // If a bluetooth device has been connected
    private var connected = false
    // The name of the default connection
    private var deviceName : String? = null



    /** Manages the connections with bluetooth devices */
    private var bluetoothManager: BluetoothManager? = null
    /** Manages the sending and receiving data with bluetooth devices */
    private var bluetoothAdapter: BluetoothAdapter? = null
    /** Holds the data that will be sent via bluetooth */
    private var cache : Int? = null

    fun setCache(data: Int) {
        cache = data
    }
    fun getCache(): Int? {
        return cache
    }

    /**
     * Provides the initial setup for the bluetooth manager on launch
     * @param context - The context of the Activity bluetooth will be used in
     * @param activity - the Activity bluetooth will be used in
     */
    fun bluetoothSetUp() {
        // Bluetooth setup on application launch

        // When the app starts requests bluetooth permissions if not already granted
        // *Bluetooth permissions are automatically granted most of the time
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN),
                REQUEST_ENABLE_BT
            )
        }

        bluetoothManager = getSystemService(applicationContext, BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter

        // When the app starts if Bluetooth is inactive it requests to activate it
        //      only if the device supports it
        if (bluetoothAdapter == null) {
            Toast.makeText(applicationContext,
                "This device doesn't support Bluetooth", Toast.LENGTH_LONG).show()
            return
        } else {
            if (bluetoothAdapter?.isEnabled == false) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivity(applicationContext, enableBtIntent, null)
            }
            findBT()
        }

        // Done with bluetooth setup on application launch
    }

    @SuppressLint("MissingPermission")
    private fun findBT() {
        // Selects a device to connect
        val devices = bluetoothAdapter!!.bondedDevices
        val list: ArrayList<BluetoothDevice> = ArrayList()

        if (devices.isNotEmpty()) {
            for (device in devices) {
                // Automatically connects to the previously connected device
                //      unless it isn't currently paired
                if (device.name == deviceName && deviceName != null) {
                    mmDevice = device
                    connected = true
                    Toast.makeText(applicationContext, "Bluetooth Device Found", Toast.LENGTH_LONG).show()
                    break
                }
                list.add(device)
            }
        }
        if(list.isNotEmpty()) {
            // Show user a list of BT devices to allow the user to select a specific device
            viewModel.selectList(list)

            // TODO: fix this
            // Send user to the fragment with the list of devices
            navController.navigate(R.id.action_FirstFragment_to_SecondFragment)

            // Get the user response from the list
            viewModel.currentDevice.observe(this) { item ->
                mmDevice = item
                deviceName = item.name
            }
            // Removes all items from the list to save memory
            viewModel.clearList()
        }
        else {
            // Alert that tells the user to connect to a bluetooth device
            //      only if there are no paired devices
            val dialog: AlertDialog.Builder = AlertDialog.Builder(this)
            dialog.setMessage("No possible connections\nPlease connect to a device")
            dialog.setTitle("No Devices Found")
            dialog.setNegativeButton("OK", null)
//                dialog.setPositiveButton("Connect", DialogInterface.OnClickListener {
//                        dialog, which ->
//
//
//                })
            val alertDialog = dialog.create()
            alertDialog.show()
        }
    }

    /**
     * Check if bluetooth was disabled in between launch of app and sending data
     */
    fun bluetoothActivate() {
        // If there is no bluetoothAdapter the device doesn't support bluetooth
        if (bluetoothAdapter == null) {
            Toast.makeText(applicationContext,
                "This device doesn't support Bluetooth", Toast.LENGTH_LONG).show()
        }
        // If the bluetoothAdapter is not enabled request the user enable bluetooth
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(applicationContext, enableBtIntent, null)
        }
    }

    fun sendBT(command: Int) {
        if(connected) {
            mmOutputStream.write(command)
            Toast.makeText(applicationContext, "Sending Data", Toast.LENGTH_LONG).show()
        }
    }
}