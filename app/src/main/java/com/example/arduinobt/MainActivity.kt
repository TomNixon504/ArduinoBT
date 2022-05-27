package com.example.arduinobt

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.arduinobt.databinding.ActivityMainBinding
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

// Code to request to enable bluetooth
private const val REQUEST_ENABLE_BT = 1
// Standard SerialPortService ID
private const val serialPortServiceID = "00001101-0000-1000-8000-00805F9B34FB"

open class MainActivity : AppCompatActivity() {

    // Coded bytes for interpreting data sent and received
    private val SW1_CLOSED = 0x41
    private val SW1_OPEN = 0x45
    private val SW2_CLOSED = 0x42
    private val SW2_OPEN = 0x46
    private val REQUEST_LED1_ON = 0x4A
    private val CONFIRM_LED1_ON = 0x4B
    private val REQUEST_LED1_OFF = 0x4C
    private val CONFIRM_LED1_OFF = 0x4D
    private val REQUEST_LED2_ON = 0x4E
    private val CONFIRM_LED2_ON = 0x4F
    private val REQUEST_LED2_OFF = 0x50
    private val CONFIRM_LED2_OFF = 0x51
    private val REQUEST_PASS_ON = 0x01
    private val CONFIRM_PASS_ON = 0x02
    private val REQUEST_PASS_OFF = 0x04
    private val CONFIRM_PASS_OFF = 0x03
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

    private val viewModel: ItemViewModel by viewModels()

    lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding



    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        navController = findNavController(R.id.nav_host_fragment_content_main)
//        appBarConfiguration = AppBarConfiguration(navController.graph)
//        setupActionBarWithNavController(navController, appBarConfiguration)

        // Finds and connects to Bluetooth device
        findBT()
        // Starts listening for data from Bluetooth device
        openBT()
    }

    override fun onStop() {
        super.onStop()
        closeBT()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }


    private fun findBT() {
        // Ensure that the required permissions for bluetooth are given
        if (ActivityCompat.checkSelfPermission
                (applicationContext, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN),
                REQUEST_ENABLE_BT)
        }

        val bluetoothManager : BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter : BluetoothAdapter? = bluetoothManager.adapter

        // If the device supports bluetooth then request bluetooth activation if not active
        if (bluetoothAdapter == null) {
            Toast.makeText(applicationContext, "No Bluetooth Adapter", Toast.LENGTH_LONG).show()
            return
        }
        else {
            if (!bluetoothAdapter.isEnabled) {
                val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivity(enableBluetooth, null)
            }

            // Selects a device to connect
            val devices = bluetoothAdapter.bondedDevices
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
    }

    private fun openBT() {
        val uuid: UUID =
            UUID.fromString(serialPortServiceID)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetooth.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ContextCompat.startActivity(applicationContext, enableBluetooth, null)
        }
        if(connected) {
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid)
            mmSocket.connect()
            mmOutputStream = mmSocket.outputStream
            mmInputStream = mmSocket.inputStream
            beginListenForData()
        }
    }

    private fun closeBT() {
        if(connected) {
            stopWorker = true
            mmOutputStream.close()
            mmInputStream.close()
            mmSocket.close()
        }
    }

//    fun sendBT(command: Int) {
//        if(connected) {
//            mmOutputStream.write(command)
//            Toast.makeText(applicationContext, "Sending Data", Toast.LENGTH_LONG).show()
//        }
//    }

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