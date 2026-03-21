//TODO:
//8. switch all errors over to logcat logs
//3. write data to a file
// make the page auto-refresh

package com.example.a7_0project;

import static android.os.SystemClock.uptimeMillis;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.example.http_file_server.HttpServerService;

public class MainActivity extends AppCompatActivity {

    private static final String ACTION_USB_PERMISSION = "com.android.example.a7_0project.USB_PERMISSION";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String PREFS_NAME = "BluetoothPrefs";
    private static final String KEY_DEFAULT_DEVICE = "DefaultDeviceAddress";

    private UsbManager usbManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private boolean isBluetoothConnecting = false;

    //data that needs to be tracked across frames
    private long row;
    private int lap_number = 0;
    private long lap_start_timestamp;
    private long race_start_timestamp;
    private double distance;
    private File current_file;

    //all the data that we will be loggin'
    private Map<String, Object> data_frame = new ConcurrentHashMap<>();

    private TextView label1;
    private TextView label2;
    private StringMessageBuilder currentMessageBuilder;
    private TextView RpmNumber;
    private TextView RpmNumber2;
    private TextView SpeedLabel;
    private TextView SpeedNumber;
    private TextView SpeedNumber2;
    private ProgressBar RedlineIndicator;
    private ProgressBar ThrottlePositionBar;
    private Button RecordingButton;
    private TextView VoltageNumber;
    private TextView FuelPressure;
    private TextView LambdaNumber;
    private TextView CoolantTemperature;
    private Button StarterIndicator;
    private Button EngineIndicator;
    private TextView BurnOrCoast;
    private TextView GPSSpeed;
    private TextView Latitude;
    private TextView Longitude;
    private TextView IPAddress;
    private TextView rawBluetoothData;

    private View DriverView;
    private View StatsView;

    // Bluetooth UI components (now integrated into main activity)
    private ListView deviceList;
    private Button refreshButton;
    private ArrayList<String> deviceNames = new ArrayList<>();
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private SerialInputOutputManager usbIoManager;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private double gps_speed = -1;
    private int LOCATION_REQUEST_CODE = 100;
    private TextView ErrorNotif;
    private long errorTimestamp;
    private long ecu_speed_timestamp;
    public static boolean threadLock = false;

    //how often the log function runs in milliseconds
    public static int log_timing = 100;

    private final WarningBundle[] warningObjects = new WarningBundle[9];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //start the HTTP server to show internal files for download
        Intent intent = new Intent(this, HttpServerService.class);
        startService(intent);

        //hide the home button, notification bar, and that weird bar with the app name
        hideSystemUI();

        setContentView(R.layout.layout);

        //get references to all the UI elements
        label1 = findViewById(R.id.label1);
        label2 = findViewById(R.id.label2);
        ErrorNotif = findViewById(R.id.errorNotif);
        RpmNumber = findViewById(R.id.rpmNumber);
        RpmNumber2 = findViewById(R.id.rpmNumber2);
        SpeedLabel = findViewById(R.id.speedLabel);
        SpeedNumber = findViewById(R.id.speedNumber);
        SpeedNumber2 = findViewById(R.id.speedNumber2);
        RedlineIndicator = findViewById(R.id.redlineIndicator);
        ThrottlePositionBar = findViewById(R.id.throttlePositionBar);
        RecordingButton = findViewById(R.id.recordingButton);
        VoltageNumber = findViewById(R.id.voltageNumber);
        FuelPressure = findViewById(R.id.fuelPressureNumber);
        LambdaNumber = findViewById(R.id.lambdaNumber);
        CoolantTemperature = findViewById(R.id.coolantTempNumber);
        StarterIndicator = findViewById(R.id.starterIndicator);
        EngineIndicator = findViewById(R.id.engineIndicator);
        BurnOrCoast = findViewById(R.id.burnOrCoast);
        GPSSpeed = findViewById(R.id.gpsSpeedNumber);
        Latitude = findViewById(R.id.latitudeNumber);
        Longitude = findViewById(R.id.longitudeNumber);
        IPAddress = findViewById(R.id.IPAddrNumber);
        rawBluetoothData = findViewById(R.id.raw_bluetooth_data);
        DriverView = findViewById(R.id.driver_view);
        StatsView = findViewById(R.id.stats_view);

        // Bluetooth Setup
        deviceList = findViewById(R.id.device_list);
        refreshButton = findViewById(R.id.refresh_button);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
        if (deviceList != null) {
            deviceList.setAdapter(adapter);
            deviceList.setOnItemClickListener((parent, view, position, id) -> {
                BluetoothDevice selectedDevice = devices.get(position);
                saveDefaultDevice(selectedDevice.getAddress());
                connectToBluetoothDevice(selectedDevice);
                Toast.makeText(this, "Selected: " + selectedDevice.getName(), Toast.LENGTH_SHORT).show();
            });
        }
        if (refreshButton != null) {
            refreshButton.setOnClickListener(v -> listPairedDevices());
        }

        warningObjects[0] = new WarningBundle(
                findViewById(R.id.coolantImage),
                findViewById(R.id.coolantWarningLabel),
                CoolantTemperature, findViewById(R.id.coolantTempLabel)
        );
        warningObjects[2] = new WarningBundle(
                findViewById(R.id.engineSpeedImage),
                findViewById(R.id.engineSpeedLabel),
                RpmNumber, findViewById(R.id.rpmLabel)
        );
        warningObjects[5] = new WarningBundle(
                findViewById(R.id.batteryWarningImage),
                findViewById(R.id.batteryWarningLabel),
                VoltageNumber, findViewById(R.id.voltageLabel)
        );
        warningObjects[6] = new WarningBundle(
                findViewById(R.id.fuelPressureImage),
                findViewById(R.id.fuelPressureWarningLabel),
                FuelPressure, findViewById(R.id.fuelPressureLabel)
        );

        currentMessageBuilder = new StringMessageBuilder();

        //request fine location permission and bluetooth
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, LOCATION_REQUEST_CODE);

        //location manager object to start and stop tracking location
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        Handler log_file_write_handler = new Handler();
        log_file_write_handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (IPAddress != null) IPAddress.setText("http://" + getWifiIpAddress() + ":8080");
                if (race_start_timestamp != 0) {
                    append_csv();
                }
                data_frame.clear();
                log_file_write_handler.postDelayed(this, log_timing);
            }
        }, log_timing);

        //define a location listener
        locationListener = location -> {
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            gps_speed = location.getSpeed();
            double time_millis = location.getTime();
            data_frame.put("gps_time", time_millis);
            if (Latitude != null) Latitude.setText(String.format(Locale.ENGLISH, "%.4f", lat));
            data_frame.put("gps_latitude", String.format(Locale.ENGLISH, "%.4f", lat));
            if (Longitude != null) Longitude.setText(String.format(Locale.ENGLISH, "%.4f", lon));
            data_frame.put("gps_longitude", String.format(Locale.ENGLISH, "%.4f", lon));
            if (GPSSpeed != null) GPSSpeed.setText(String.format(Locale.ENGLISH, "%.2f", gps_speed));
            data_frame.put("gps_speed", gps_speed);
            if ((uptimeMillis() - ecu_speed_timestamp) > 500) {
                if (SpeedLabel != null) SpeedLabel.setTextColor(Color.CYAN);
                handleSpeed(gps_speed * 2.23693629);
            }
        };

        BurnOrCoast.setOnClickListener(v -> toggleStatsView());
        startGPSTracking();

        RecordingButton.setOnClickListener(v -> temp_logging_start());

        this.usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        ViewFlipper viewFlipper = findViewById(R.id.viewSwitcher);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (viewFlipper != null) {
                    viewFlipper.setDisplayedChild(tab.getPosition());
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        if (label1 != null) label1.setText(getWifiIpAddress());
        if (IPAddress != null) IPAddress.setText("http://" + getWifiIpAddress() + ":8080");

        Intent this_intent = getIntent();
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(this_intent.getAction())) {
            Log.i("SSE", "Activity started by USB device attachment.");
        }

        auto_reconnect();
        startBluetoothAutoConnect();
        listPairedDevices();
    }

    private void startBluetoothAutoConnect() {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkDefaultBluetoothDevice();
                handler.postDelayed(this, 500); // Check every 2 seconds
            }
        }, 500);
    }

    private void checkDefaultBluetoothDevice() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return;
        }

        if (isBluetoothConnecting || (bluetoothSocket != null && bluetoothSocket.isConnected())) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String address = prefs.getString(KEY_DEFAULT_DEVICE, null);
        if (address != null) {
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                connectToBluetoothDevice(device);
            } catch (IllegalArgumentException e) {
                Log.e("SSE", "Invalid Bluetooth address: " + address);
            }
        }
    }

    public void connectToBluetoothDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (isBluetoothConnecting) return;
        isBluetoothConnecting = true;
        
        new Thread(() -> {
            BluetoothSocket tempSocket = null;
            try {
                tempSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                tempSocket.connect();
                
                synchronized (this) {
                    if (bluetoothSocket != null) {
                        try { bluetoothSocket.close(); } catch (IOException ignored) {}
                    }
                    bluetoothSocket = tempSocket;
                }
                
                isBluetoothConnecting = false;
                runOnUiThread(() -> Toast.makeText(this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show());
                startBluetoothReader();
            } catch (IOException e) {
                isBluetoothConnecting = false;
                if (tempSocket != null) {
                    try { tempSocket.close(); } catch (IOException ignored) {}
                }
                Log.d("SSE", "Bluetooth connection failed (device probably off)");
            }
        }).start();
    }

    private void startBluetoothReader() {
        new Thread(() -> {
            try (InputStream inputStream = bluetoothSocket.getInputStream()) {
                byte[] buffer = new byte[1024];
                int bytes;
                while (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                    bytes = inputStream.read(buffer);
                    if (bytes == -1) break;
                    if (bytes > 0) {
                        byte[] data = Arrays.copyOf(buffer, bytes);
                        runOnUiThread(() -> {
                            if (rawBluetoothData != null) {
                                rawBluetoothData.setText(Arrays.toString(data));
                            }
                            for (byte b : data) {
                                MessageBuilderState msgb_state = currentMessageBuilder.add(b);
                                if (msgb_state == MessageBuilderState.COMPLETE) {
                                    handle_complete_message(currentMessageBuilder.getMessage());
                                }
                            }
                        });
                    }
                }
            } catch (IOException e) {
                Log.e("SSE", "Bluetooth read error", e);
            } finally {
                synchronized (this) {
                    if (bluetoothSocket != null) {
                        try { bluetoothSocket.close(); } catch (IOException ignored) {}
                        bluetoothSocket = null;
                    }
                }
            }
        }).start();
    }

    private void listPairedDevices() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void saveDefaultDevice(String address) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_DEFAULT_DEVICE, address).apply();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            Log.i("SSE", "USB device attached while Activity is running.");
            if (usbIoManager == null || usbIoManager.getState() == SerialInputOutputManager.State.STOPPED) {
                auto_reconnect();
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbPermissionReceiver, filter, RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(usbPermissionReceiver);
    }

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    Log.i("SSE", "USB permission GRANTED by user.");
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null) {
                        try_to_connect_after_permission(device);
                    }
                } else {
                    Log.w("SSE", "USB permission DENIED by user.");
                    pushAlert("USB Permission", "Permission denied for USB device. Cannot connect.", "OK");
                }
            }
        }
    };

    private void auto_reconnect() {
        if (label1 != null) label1.setText("Attempting autoreconnect...");
        final Handler handler = new Handler();
        final int delay = 100;

        handler.postDelayed(new Runnable() {
            public void run() {
                if (label2 != null) label2.setText("Checking for USB device...");

                if (usbIoManager != null && usbIoManager.getState() != SerialInputOutputManager.State.STOPPED) {
                    if (label1 != null) label1.setText("Connected");
                    return;
                }

                if (usbManager.getDeviceList().isEmpty()) {
                    if (label2 != null) label2.setText("No device found, trying again...");
                    handler.postDelayed(this, delay);
                    return;
                }

                UsbDevice device = usbManager.getDeviceList().values().iterator().next();

                if (usbManager.hasPermission(device)) {
                    if (label2 != null) label2.setText("Permission OK. Connecting...");
                    try_to_connect_after_permission(device);
                } else {
                    if (label2 != null) label2.setText("Requesting USB permission...");
                    PendingIntent permissionIntent = PendingIntent.getBroadcast(
                            MainActivity.this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
                    );
                    usbManager.requestPermission(device, permissionIntent);
                }
                handler.postDelayed(this, delay);
            }
        }, delay);
    }

    private void try_to_connect_after_permission(UsbDevice device) {
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            pushAlert("Error", "No USB serial drivers available.", "OK");
            return;
        }

        UsbSerialDriver driver = null;
        for (UsbSerialDriver d : availableDrivers) {
            if (d.getDevice().equals(device)) {
                driver = d;
                break;
            }
        }

        if (driver == null) {
            Log.e("SSE", "No driver found for the permitted device.");
            pushAlert("Error", "No compatible driver found.", "OK");
            return;
        }

        UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());
        if (connection == null) {
            return;
        }

        UsbSerialPort port = driver.getPorts().get(0);

        try {
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        SerialHandler serialHandler = new SerialHandler();
        this.usbIoManager = new SerialInputOutputManager(port, serialHandler);
        usbIoManager.start();
    }

    public class SerialHandler extends Fragment implements SerialInputOutputManager.Listener {
        @Override
        public void onNewData(byte[] data) {
            runOnUiThread(() -> {
                try {
                    for (byte b : data) {
                        MessageBuilderState msgb_state = currentMessageBuilder.add(b);
                        if (msgb_state == MessageBuilderState.COMPLETE) {
                            handle_complete_message(currentMessageBuilder.getMessage());
                        }
                    }
                    threadLock = false;
                } catch (Exception e) {
                    pushAlert("ERROR (newData)", e.getMessage() + Arrays.toString(e.getStackTrace()), "darn");
                }
            });
        }

        @Override
        public void onRunError(Exception e) {
            usbIoManager.stop();
            pushAlert("On Run Error Called", Arrays.toString(e.getStackTrace()), "yikes");
            auto_reconnect();
        }
    }

    private void temp_logging_start() {
        try {
            if (race_start_timestamp == 0) {
                create_csv();
                if (SpeedLabel != null) SpeedLabel.setTextColor(Color.RED);
                race_start_timestamp = System.currentTimeMillis();
            }
            else {
                if (RecordingButton != null) RecordingButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#541414")));
                race_start_timestamp = 0;
            }
        } catch (Exception e) {
            if (RecordingButton != null) RecordingButton.setBackgroundTintList(ColorStateList.valueOf(Color.BLUE));
            if (e.getMessage() != null) Log.e("SSE", e.getMessage());
        }
    }

    private void handle_complete_message(Message message) {
        try {
            currentMessageBuilder = new StringMessageBuilder();
            if (message != null) {
                switch (message.messageID) {
                    case 0x640:
                        int rpm = message.getContentVariable(0, 16);
                        if (RpmNumber != null) RpmNumber.setText(String.format(Locale.ENGLISH, "%d", rpm));
                        if (RpmNumber2 != null) RpmNumber2.setText(String.format(Locale.ENGLISH, "%d", rpm));
                        if (EngineIndicator != null) EngineIndicator.setBackgroundTintList(ColorStateList.valueOf( rpm > 1600 ? Color.GREEN :Color.RED));
                        if (StarterIndicator != null) StarterIndicator.setBackgroundTintList(ColorStateList.valueOf( rpm > 100 && rpm < 1500 ? Color.GREEN :Color.RED));
                        break;
                    case 0x641:
                        int fuelPressure = message.getContentVariable(32, 16) / 10;
                        if (FuelPressure != null) FuelPressure.setText(String.format(Locale.ENGLISH, "%d kPa", fuelPressure));
                        data_frame.put("fuel_pressure", String.format("%d", fuelPressure));
                        int fuelInjectorTiming = message.getContentVariable(48, 8);
                        data_frame.put("fuel_injector_timing", String.format("%d", fuelInjectorTiming));
                        int engineEfficiency = message.getContentVariable(56, 8);
                        data_frame.put("engine_efficiency", String.format("%d", engineEfficiency));
                        break;
                    case 0x642:
                        int engineLoad = message.getContentVariable(16, 16);
                        data_frame.put("engine_load", String.format("%d", engineLoad));
                        break;
                    case 0x649:
                        int engineOilTemp = message.getContentVariable(8, 8) - 40;
                        data_frame.put("oil_temp", String.format("%d", engineOilTemp));
                        float voltage = message.getContentVariable(40, 8) / (float) 10;
                        if (VoltageNumber != null) VoltageNumber.setText(String.format(Locale.ENGLISH, "%.1f V", voltage));
                        data_frame.put("battery_voltage", String.format("%.1f", voltage));
                        if (voltage < 10.5) {
                            if (warningObjects[5] != null) warningObjects[5].timestamp = System.currentTimeMillis();
                        }
                        break;
                    case 0x64c:
                        for (int i = 0; i < 9; i++) {
                            if (message.getContentVariable(40 + i, 1) == 1) {
                                if (warningObjects[i] != null) {
                                    warningObjects[i].timestamp = System.currentTimeMillis();
                                }
                            }
                        }
                        break;
                    case 0x460:
                        if (message.getContentVariable(0, 8) == 0) {
                            double lambda = message.getContentVariable(8, 16) / 1000.0;
                            if (LambdaNumber != null) LambdaNumber.setText(String.format(Locale.ENGLISH, "%4.3f", lambda));
                        }
                        break;
                    case 0x118:
                        double ecu_speed = (message.getContentVariable(16, 8) * 0.6213712);
                        if (SpeedLabel != null) SpeedLabel.setTextColor(Color.GREEN);
                        handleSpeed(ecu_speed);
                        int throttlePosition = message.getContentVariable(8, 8);
                        if (ThrottlePositionBar != null) ThrottlePositionBar.setProgress(throttlePosition);
                        data_frame.put("throttle_position", String.format(Locale.ENGLISH, "%d", throttlePosition));
                        int coolant_temp = message.getContentVariable(24, 8);
                        if (CoolantTemperature != null) CoolantTemperature.setText(String.format(Locale.ENGLISH, "%d C", coolant_temp));
                        break;
                }
                handle_warnings();
            }
        } catch (Exception e) {
            Log.e("SSE", "Complete message error", e);
        }
    }

    public void handleSpeed(double vehicle_speed) {
        if (SpeedNumber != null) SpeedNumber.setText(String.format(Locale.ENGLISH, "%.1f", vehicle_speed));
        if (SpeedNumber2 != null) SpeedNumber2.setText(String.format(Locale.ENGLISH, "%.1f", vehicle_speed));
        if (RedlineIndicator != null) {
            RedlineIndicator.setProgress((int) (vehicle_speed * 10));
            RedlineIndicator.setProgressTintList(ColorStateList.valueOf(vehicle_speed > 26 || vehicle_speed < 10 ? Color.RED : Color.YELLOW));
            if (vehicle_speed < 24 || vehicle_speed > 13) {
                RedlineIndicator.setProgressTintList(ColorStateList.valueOf(Color.GREEN));
            }
        }
        if (BurnOrCoast != null) {
            if (vehicle_speed > 24) {
                BurnOrCoast.setText("Coast!");
                BurnOrCoast.setTextColor(Color.GREEN);
            }
            if (vehicle_speed < 13) {
                BurnOrCoast.setText("Burn!");
                BurnOrCoast.setTextColor(Color.RED);
            }
        }
    }

    public void handle_warnings() {
        for (WarningBundle bundle : warningObjects) {
            if (bundle != null) {
                boolean active = (System.currentTimeMillis() - bundle.timestamp) < 6000;
                boolean flashedOn = ((System.currentTimeMillis() - bundle.timestamp) % 1000 < 650) && active;
                if (bundle.warningImage != null) bundle.warningImage.setAlpha((float) (flashedOn ? 1.0 : 0.2));
                if (bundle.warningLabel != null) bundle.warningLabel.setAlpha((float) (flashedOn ? 1.0 : 0.2));
                if (bundle.number != null) bundle.number.setTextColor(active ? Color.RED : Color.GREEN);
                if (bundle.label != null) bundle.label.setTextColor(active ? Color.RED : Color.GREEN);
            }
        }
    }

    private static class WarningBundle {
        ImageView warningImage;
        TextView warningLabel;
        TextView number;
        TextView label;
        long timestamp = 0;

        public WarningBundle(ImageView warningImage, TextView warningLabel, TextView number, TextView label) {
            this.label = label;
            this.number = number;
            this.warningLabel = warningLabel;
            this.warningImage = warningImage;
        }
    }

    public void pushAlert(String title, String message, String buttonText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(message);
        builder.setTitle(title);
        builder.setCancelable(true);
        builder.setNegativeButton(buttonText, (dialog, which) -> dialog.cancel());
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void create_csv() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd__HH-mm-ss", Locale.getDefault());
        String filename = sdf.format(new Date()) + ".csv";
        File directory = getFilesDir();
        this.current_file = new File(directory, filename);
        this.lap_number = 0;
        this.row = 0;
        this.distance = 0;
        this.race_start_timestamp = System.currentTimeMillis();
        this.lap_start_timestamp = 0;
        this.data_frame.clear();

        try (FileWriter writer = new FileWriter(this.current_file)) {
            String headerLine = String.join(",", csv_headers);
            writer.write(headerLine);
            writer.write("\n");
            writer.flush();
            Log.i("SSE", "Successfully created and wrote headers to " + this.current_file.getName());
        } catch (IOException e) {
            Log.e("SSE", "Error writing to CSV file", e);
        }
    }

    private void append_csv() {
        if (this.current_file == null) return;
        data_frame.put("unix_timestamp", System.currentTimeMillis());
        data_frame.put("lap_unix_timestamp", System.currentTimeMillis() - this.lap_start_timestamp);
        data_frame.put("row", row++);
        data_frame.put("lap", lap_number);
        data_frame.put("distance", distance);

        try (FileWriter writer = new FileWriter(this.current_file, true)) {
            List<String> rowValues = new ArrayList<>();
            for (String header : csv_headers) {
                Object value = this.data_frame.get(header);
                if (value != null) {
                    if (value instanceof Double || value instanceof Float) {
                        rowValues.add(String.format(Locale.ENGLISH,"%.2f", value));
                    } else {
                        rowValues.add(value.toString());
                    }
                } else {
                    rowValues.add("");
                }
            }
            writer.write(String.join(",", rowValues));
            writer.write("\n");
            writer.flush();
        } catch (IOException e) {
            Log.e("SSE", "Error appending data to CSV file", e);
        }
    }

    public String[] csv_headers = {
            "row", "unix_timestamp", "lap_unix_timestamp", "distance", "lap_distance", "gps_time",
            "gps_latitude", "gps_longitude", "gps_speed", "throttle_position", "engine_efficiency",
            "oil_temp", "engine_load", "wheel_speed_ecu", "wheel_speed_arduino", "fuel_injector_timing",
            "acceleration_lateral", "acceleration_longitudinal", "acceleration_vertical", "fuel_pressure",
            "battery_voltage", "message_rec_bits"
    };

    public static class Message {
        public int messageType;
        public int messageID;
        public long messageContent;
        public Message(int messageType, int messageID, long messageContent) {
            this.messageType = messageType;
            this.messageID = messageID;
            this.messageContent = messageContent;
        }
        public int getContentVariable(int offset, int length) {
            return (int) (messageContent >> (64 - offset - length)) & (int) (Math.pow(2, length) - 1);
        }
    }

    enum MessageBuilderState {WAITING, MESSAGE_TYPE, MESSAGE_ID, MESSAGE_CONTENT, COMPLETE}

    class StringMessageBuilder {
        int messageType = 0;
        int messageID = 0;
        long messageContent = 0;
        int remainingBytes = 0;
        StringBuilder stringBuilder;
        private MessageBuilderState state;
        public StringMessageBuilder() {
            this.state = MessageBuilderState.WAITING;
            this.stringBuilder = new StringBuilder();
        }
        MessageBuilderState add(byte b) {
            switch (this.state) {
                case WAITING:
                    stringBuilder = new StringBuilder();
                    if ((char) b == '[') {
                        this.state = MessageBuilderState.MESSAGE_TYPE;
                        remainingBytes = 2;
                    }
                    break;
                case MESSAGE_TYPE:
                    if (remainingBytes == 2 && (char) b == '1') {
                        messageType = 1;
                        remainingBytes--;
                    }
                    if (remainingBytes == 1 && (char) b == ']') {
                        this.state = MessageBuilderState.MESSAGE_CONTENT;
                    }
                    break;
                case MESSAGE_CONTENT:
                    if ((char) b == '\n') {
                        this.state = MessageBuilderState.COMPLETE;
                        break;
                    }
                    stringBuilder.append((char) b);
                    break;
            }
            return state;
        }
        public Message getMessage() {
            String result = stringBuilder.toString();
            String[] hexBytes = result.trim().split(" ");
            try {
                this.messageID = this.messageID | Integer.parseInt(hexBytes[0], 16);
                for (int i = 1; i < hexBytes.length; i++) {
                    this.messageContent = this.messageContent << 8;
                    this.messageContent = this.messageContent | Integer.parseInt(hexBytes[i], 16);
                }
            } catch (Exception e) {}
            if (state == MessageBuilderState.COMPLETE) {
                return new Message(this.messageType, this.messageID, this.messageContent);
            } else {
                return null;
            }
        }
    }
    
    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void toggleStatsView() {
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        if (tabLayout != null) {
            int currentTab = tabLayout.getSelectedTabPosition();
            TabLayout.Tab nextTab = tabLayout.getTabAt((currentTab + 1) % 2);
            if (nextTab != null) nextTab.select();
        }
    }

    private void startGPSTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0, locationListener);
        if (Latitude != null) Latitude.setText("ran");
    }

    private String getWifiIpAddress() {
        Context context = getApplicationContext();
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        int ip = wm.getConnectionInfo().getIpAddress();
        if (ip != 0) {
            return String.format(Locale.ENGLISH, "%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
        }
        return "No Local IP Address";
    }
}
