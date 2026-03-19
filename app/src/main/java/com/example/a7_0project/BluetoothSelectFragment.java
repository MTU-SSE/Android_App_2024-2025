package com.example.a7_0project;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Set;

public class BluetoothSelectFragment extends Fragment {

    private ListView deviceList;
    private Button refreshButton;
    private BluetoothAdapter bluetoothAdapter;
    private ArrayList<String> deviceNames = new ArrayList<>();
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private static final String PREFS_NAME = "BluetoothPrefs";
    private static final String KEY_DEFAULT_DEVICE = "DefaultDeviceAddress";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bluetooth_select, container, false);

        deviceList = view.findViewById(R.id.device_list);
        refreshButton = view.findViewById(R.id.refresh_button);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, deviceNames);
        deviceList.setAdapter(adapter);

        refreshButton.setOnClickListener(v -> listPairedDevices());

        deviceList.setOnItemClickListener((parent, view1, position, id) -> {
            BluetoothDevice selectedDevice = devices.get(position);
            saveDefaultDevice(selectedDevice.getAddress());
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).connectToBluetoothDevice(selectedDevice);
            }
            Toast.makeText(getContext(), "Selected: " + selectedDevice.getName(), Toast.LENGTH_SHORT).show();
        });

        listPairedDevices();

        return view;
    }

    private void listPairedDevices() {
        if (bluetoothAdapter == null) {
            Toast.makeText(getContext(), "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // Permissions should be handled in MainActivity or here if needed.
            // For simplicity, assuming granted as they are in manifest.
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        deviceNames.clear();
        devices.clear();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                deviceNames.add(device.getName() + "\n" + device.getAddress());
                devices.add(device);
            }
        } else {
            deviceNames.add("No paired devices found");
        }
        adapter.notifyDataSetChanged();
    }

    private void saveDefaultDevice(String address) {
        SharedPreferences prefs = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_DEFAULT_DEVICE, address).apply();
    }
}
