package com.example.arduinobt

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.example.arduinobt.databinding.FragmentFirstBinding
import java.util.*
import kotlin.collections.ArrayList

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
open class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    val viewModel: ItemViewModel by viewModels()
    private val controlBT = MyBluetoothController()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Finds and connects to Bluetooth device
        val list = context?.let { controlBT.bluetoothSetUp(it, activity) }
        if (list != null) {
            viewModel.selectList(list)
        }
        if(viewModel.currentList.value != null && viewModel.currentList.value!!.isNotEmpty()) {
            connectBT(viewModel.currentList.value!!)
        }
        // Starts listening for data from Bluetooth device
        controlBT.openBT(requireContext())

        binding.LED1.setOnClickListener {
            Toast.makeText(context, "LED1", Toast.LENGTH_SHORT).show()
        }

        binding.LED2.setOnClickListener {
            Toast.makeText(context, "LED2", Toast.LENGTH_SHORT).show()
        }

        binding.SW1.setOnClickListener {

        }

        binding.SW2.setOnClickListener {

        }

        binding.passThrough.setOnClickListener {

        }

            //findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("MissingPermission")
    fun connectBT(list: ArrayList<BluetoothDevice>) {
        if(list.isNotEmpty()) {
            // Show user a list of BT devices to allow the user to select a specific device

            // Send user to the fragment with the list of devices
            val nameList = ArrayList<String>(list.size)

            for (device in list) {
                var i = 0
                nameList[i] = device.name
                i++
            }
            val bundle = Bundle(nameList.size)
            bundle.putStringArrayList("names", nameList)

            val navHost = NavHostFragment.create(R.navigation.nav_graph, bundle)
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)

            // Get the user response from the list
            viewModel.currentDevice.observe(viewLifecycleOwner) { item ->
                controlBT.mmDevice = item
                controlBT.deviceName = item.name
            }
            // Removes all items from the list to save memory
            viewModel.clearList()
        }
        else {
            // Alert that tells the user to connect to a bluetooth device
            //      only if there are no paired devices
            val dialog: AlertDialog.Builder = AlertDialog.Builder(context)
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