package com.example.a7_0project;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
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
    private TextView bluetoothStatus;
    private BluetoothAdapter bluetoothAdapter;
    private ArrayList<String> deviceNames = new ArrayList<>();
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private static final String PREFS_NAME = "BluetoothPrefs";
    private static final String KEY_DEFAULT_DEVICE = "DefaultDeviceAddress";
    
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private Runnable statusUpdater;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bluetooth_select, container, false);

        deviceList = view.findViewById(R.id.device_list);
        refreshButton = view.findViewById(R.id.refresh_button);
        bluetoothStatus = view.findViewById(R.id.bluetooth_status);

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
        startStatusUpdateLoop();

        return view;
    }

    private void startStatusUpdateLoop() {
        statusUpdater = new Runnable() {
            @Override
            public void run() {
                updateStatusUI();
                statusHandler.postDelayed(this, 500);
            }
        };
        statusHandler.post(statusUpdater);
    }

    private void updateStatusUI() {
        if (getActivity() instanceof MainActivity && bluetoothStatus != null) {
            MainActivity mainActivity = (MainActivity) getActivity();
            boolean connected = mainActivity.isBluetoothConnected();
            boolean connecting = mainActivity.isBluetoothConnecting();
            
            if (connected) {
                bluetoothStatus.setText("Status: Connected");
                bluetoothStatus.setTextColor(Color.GREEN);
            } else if (connecting) {
                bluetoothStatus.setText("Status: Connecting...");
                bluetoothStatus.setTextColor(Color.YELLOW);
            } else {
                bluetoothStatus.setText("Status: Disconnected");
                bluetoothStatus.setTextColor(Color.RED);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (statusHandler != null && statusUpdater != null) {
            statusHandler.removeCallbacks(statusUpdater);
        }
    }

    private void listPairedDevices() {
        if (bluetoothAdapter == null) {
            Toast.makeText(getContext(), "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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
