package com.example.arduinobt

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.arduinobt.databinding.FragmentListBinding
import java.io.IOException

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class ListFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ItemViewModel by activityViewModels()
    private var list: ArrayList<BluetoothDevice> = ArrayList()

    private var stop = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if(this.isVisible) {
            list = viewModel.currentList.value!!
            val adapter = ArrayAdapter(requireContext(), R.layout.fragment_list, list)

            binding.DeviceList.adapter = adapter
            binding.DeviceList.onItemClickListener = AdapterView.OnItemClickListener {
                    _, _, position, _ ->
                viewModel.selectDevice(list[position])
                findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
            }

            binding.floatingActionButton2.setOnClickListener {
                findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}