package com.example.arduinobt

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ItemViewModel allows for the transfer of data between the MainActivity and the ListFragment
 * @author Joseph Nixon
 */
class ItemViewModel : ViewModel() {
    /**  Variables to allow data to be accessed and changed by both fragments and activities  */
    private val list = MutableLiveData<ArrayList<BluetoothDevice>>()
    private val device = MutableLiveData<BluetoothDevice>()
    val currentList: LiveData<ArrayList<BluetoothDevice>> get() = list
    val currentDevice : LiveData<BluetoothDevice> get() = device

    /**
     * Allows for the change of the BluetoothDevice List
     * @param item - An ArrayList<BluetoothDevice> That contains all the possible connections for the app
     */
    fun selectList(item: ArrayList<BluetoothDevice>) {
        list.value = item
    }

    /**
     * Allows for the change of the selected Bluetooth Device
     * @param item - A BluetoothDevice that was selected by the user as the Arduino
     */
    fun selectDevice(item: BluetoothDevice) {
        device.value = item
    }

    /**
     * Clears all items from the List
     *      since a device can have a large amount of paired bluetooth devices
     *      this is a way to decrease the size of the application during runtime
     */
    fun clearList() {
        list.value?.clear()
        currentList.value?.clear()
    }
}