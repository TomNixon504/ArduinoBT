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
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.findNavController
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*


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

class MyBluetoothController {

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
    lateinit var mmDevice : BluetoothDevice
    private lateinit var mmSocket : BluetoothSocket
    private lateinit var mmOutputStream : OutputStream
    private lateinit var mmInputStream : InputStream
    private var stopWorker = false
    private var commsError = false
    // If a bluetooth device has been connected
    private var connected = false
    // The name of the default connection
    var deviceName : String? = null

    private lateinit var myContext: Context

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
    fun bluetoothSetUp(contextGiven: Context, activity: FragmentActivity?): ArrayList<BluetoothDevice>? {
        // Bluetooth setup on application launch

        myContext = contextGiven

        // When the app starts requests bluetooth permissions if not already granted
        // *Bluetooth permissions are automatically granted most of the time
        if (ActivityCompat.checkSelfPermission(
                myContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            if (activity != null) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN),
                    REQUEST_ENABLE_BT
                )
            }
        }

        bluetoothManager = getSystemService(myContext, BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter

        // When the app starts if Bluetooth is inactive it requests to activate it
        //      only if the device supports it
        return if (bluetoothAdapter == null) {
            Toast.makeText(myContext,
                "This device doesn't support Bluetooth", Toast.LENGTH_LONG).show()
            null
        } else {
            if (bluetoothAdapter?.isEnabled == false) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivity(myContext, enableBtIntent, null)
            }
            findBT()
        }

        // Done with bluetooth setup on application launch
    }

    @SuppressLint("MissingPermission")
    fun findBT(): ArrayList<BluetoothDevice> {
        // Selects a device to connect
        val devices = bluetoothAdapter!!.bondedDevices
        val list: ArrayList<BluetoothDevice> = ArrayList()

        if (devices.isNotEmpty()) {
            for (device in devices) {
                // Automatically connects to the previously connected device
                //      unless it isn't currently paired
                if (deviceName != null && device.name == deviceName) {
                    mmDevice = device
                    connected = true
                    Toast.makeText(myContext, "Bluetooth Device Found", Toast.LENGTH_LONG).show()
                    break
                }
                list.add(device)
            }
        }
        return list
    }



    fun openBT(context: Context) {
        val uuid: UUID =
            UUID.fromString(serialPortServiceID)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetooth.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(context, enableBluetooth, null)
        }
        if(connected) {
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid)
            mmSocket.connect()
            mmOutputStream = mmSocket.outputStream
            mmInputStream = mmSocket.inputStream
            beginListenForData()
        }
    }

    fun sendBT(command: Int) {
        if(connected) {
            mmOutputStream.write(command)
            Toast.makeText(myContext, "Sending Data", Toast.LENGTH_LONG).show()
        }
    }

    fun closeBT() {
        if(connected) {
            stopWorker = true
            mmOutputStream.close()
            mmInputStream.close()
            mmSocket.close()
        }
    }

    /**
     * Check if bluetooth was disabled in between launch of app and sending data
     */
    fun bluetoothActivate() {
        // If there is no bluetoothAdapter the device doesn't support bluetooth
        if (bluetoothAdapter == null) {
            Toast.makeText(
                myContext,
                "This device doesn't support Bluetooth", Toast.LENGTH_LONG).show()
        }
        // If the bluetoothAdapter is not enabled request the user enable bluetooth
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(myContext, enableBtIntent, null)
        }
    }

    private fun beginListenForData() {
        stopWorker = false
        val workerThread = Thread {
            while (!Thread.currentThread().isInterrupted && !stopWorker) {
                try {
                    val bytesAvailable = mmInputStream.available()
                    if (bytesAvailable > 0) {
                        val packetBytes = ByteArray(bytesAvailable)
                        mmInputStream.read(packetBytes)
                        for (i in 0 until bytesAvailable) {
                            val b = packetBytes[i]
                            processData(b)
                            displayChanges()
                        }
                    }
                } catch (ex: IOException) {
                    stopWorker = true
                }
            }
        }
        workerThread.start()
    }

    private fun processData(byte: Byte) {

        when(byte.toInt()) {
            SW1_CLOSED -> {
                sw1Enabled = false
            }
            SW1_OPEN -> {
                sw1Enabled = true
            }

            SW2_CLOSED -> {
                sw2Enabled = false
            }
            SW2_OPEN -> {
                sw2Enabled = true
            }

            CONFIRM_LED1_ON -> {
                if (led1Requested) {
                    led1Enabled = true
                    led1Requested = false
                } else {
                    communicationError()
                    led1Requested = false
                }
            }
            CONFIRM_LED1_OFF -> {
                if (led1Requested) {
                    led1Enabled = false
                    led1Requested = false
                } else {
                    communicationError()
                    led1Requested = false
                }
            }

            CONFIRM_LED2_ON -> {
                if (led2Requested) {
                    led2Enabled = true
                    led2Requested = false
                } else {
                    communicationError()
                    led2Requested = false
                }
            }

            CONFIRM_LED2_OFF -> {
                if (led2Requested) {
                    led2Enabled = false
                    led2Requested = false
                } else {
                    communicationError()
                    led2Requested = false
                }
            }

            CONFIRM_PASS_ON -> {
                if (passRequested) {
                    passEnabled = true
                    passRequested = false
                } else {
                    communicationError()
                    passRequested = false
                }
            }

            CONFIRM_PASS_OFF -> {
                if (passRequested) {
                    passEnabled = false
                    passRequested = false
                }
            }

            else -> {
                communicationError()
            }
        }

    }

    private fun displayChanges() {
        if(!commsError) {
            if(led1Enabled) {
                led1Image.setImageResource(androidx.appcompat.R.drawable.btn_checkbox_checked_mtrl)
            } else {
                led1Image.setImageResource(androidx.appcompat.R.drawable.btn_checkbox_unchecked_mtrl)
            }
            if(led2Enabled) {
                led2Image.setImageResource(androidx.appcompat.R.drawable.btn_checkbox_checked_mtrl)
            } else {
                led2Image.setImageResource(androidx.appcompat.R.drawable.btn_checkbox_unchecked_mtrl)
            }
            if(sw1Enabled) {
                sw1Image.setImageResource(androidx.appcompat.R.drawable.btn_checkbox_checked_mtrl)
            } else {
                sw1Image.setImageResource(androidx.appcompat.R.drawable.btn_checkbox_unchecked_mtrl)
            }
            if(sw2Enabled) {
                sw2Image.setImageResource(androidx.appcompat.R.drawable.btn_checkbox_checked_mtrl)
            } else {
                sw2Image.setImageResource(androidx.appcompat.R.drawable.btn_checkbox_unchecked_mtrl)
            }
            if(passEnabled) {
                passImage.setImageResource(androidx.appcompat.R.drawable.btn_checkbox_checked_mtrl)
            } else {
                passImage.setImageResource(androidx.appcompat.R.drawable.btn_checkbox_unchecked_mtrl)
            }
        }
    }

    private fun communicationError() {
        //TODO: figure out what to do on communication error
        commsError = true
    }
}