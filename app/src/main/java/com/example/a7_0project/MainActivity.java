//TODO:
//8. switch all errors over to logcat logs
//3. write data to a file
// make the page auto-refresh

package com.example.a7_0project;

import static android.os.SystemClock.uptimeMillis;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
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

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String PREFS_NAME = "BluetoothPrefs";
    private static final String KEY_DEFAULT_DEVICE = "DefaultDeviceAddress";
    private static final String LAP_PREFS_NAME = "LapTimingPrefs";

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private boolean isBluetoothConnecting = false;

    //data that needs to be tracked across frames
    private long row;
    private int lap_number = 0;
    private long race_start_timestamp;
    public long lap_timestamp = 0;
    public static long RACE_LENGTH = 36 * 1000 * 60;
    //public static long RACE_LENGTH = 1000 * 60;
    private double distance;
    private File current_file;

    // GPS Lap Counter variables
    private double startLatitude = 0, startLongitude = 0;
    private boolean hasLeftStartArea = false;

    //all the data that we will be loggin'
    private Map<String, Object> data_frame = new ConcurrentHashMap<>();
    private Map<String, Long> data_frame_timestamps = new ConcurrentHashMap<>();
    private Map<Integer, Integer> messageCounts = new ConcurrentHashMap<>();

    private StringMessageBuilder currentMessageBuilder;
    private TextView RpmNumber, RpmNumber2, SpeedLabel, SpeedNumber, SpeedNumber2, LapNumber,
            TimerLabel, TimerNumber, LapOffsetTimer, IdealStartCountdown, VoltageNumber, FuelPressure, LambdaNumber,
            CoolantTemperature, BurnOrCoast, GPSSpeed, Latitude, Longitude, IPAddress,
            ResetLapButton, rawBluetoothData, ErrorNotif, hasLeftStartAreaIndicator;
    private ProgressBar RedlineIndicator, ThrottlePositionBar;
    private Button RecordingButton, StarterIndicator, EngineIndicator;

    // Bluetooth UI components (now integrated into main activity)
    private ListView deviceList;
    private Button refreshButton;
    private ArrayList<String> deviceNames = new ArrayList<>();
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    // Settings UI components
    private EditText secretMultInput;
    private Button simulateLapButton;

    // Lap Timing UI components
    private EditText lap1Input, lap2Input, lap3Input, lap4Input;
    public double[] expectedLapTimes = {517.0, 517.0, 517.0, 517.0, 0, 0, 0, 0, 0, 0};

    private LocationManager locationManager;
    private LocationListener locationListener;
    private double gps_speed = -1;
    private int LOCATION_REQUEST_CODE = 100;
    private long errorTimestamp, ecu_speed_timestamp;
    public static boolean threadLock = false;

    public double secret_mult = 1.0;

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
        ErrorNotif = findViewById(R.id.errorNotif);
        RpmNumber = findViewById(R.id.rpmNumber);
        RpmNumber2 = findViewById(R.id.rpmNumber2);
        SpeedLabel = findViewById(R.id.speedLabel);
        SpeedNumber = findViewById(R.id.speedNumber);
        SpeedNumber2 = findViewById(R.id.speedNumber2);
        LapNumber = findViewById(R.id.lapNumber);
        TimerLabel = findViewById(R.id.lapTimerLabel);
        TimerNumber = findViewById(R.id.lapTimerNumber);
        LapOffsetTimer = findViewById(R.id.lapOffsetNumber);
        IdealStartCountdown = findViewById(R.id.idealStartCountdown);
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
        ResetLapButton = findViewById(R.id.resetLapButton);
        rawBluetoothData = findViewById(R.id.raw_bluetooth_data);

        // Bluetooth Setup
        deviceList = findViewById(R.id.device_list);
        refreshButton = findViewById(R.id.refresh_button);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
        if (deviceList != null) {
            deviceList.setAdapter(adapter);
            deviceList.setOnItemClickListener((parent, view, position, id) -> {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, LOCATION_REQUEST_CODE);
                    return;
                }
                BluetoothDevice selectedDevice = devices.get(position);
                saveDefaultDevice(selectedDevice.getAddress());
                connectToBluetoothDevice(selectedDevice);
                Toast.makeText(this, "Selected: " + selectedDevice.getName(), Toast.LENGTH_SHORT).show();
            });
        }
        if (refreshButton != null) {
            refreshButton.setOnClickListener(v -> listPairedDevices());
        }

        // Settings Setup
        secretMultInput = findViewById(R.id.secret_mult_input);
        simulateLapButton = findViewById(R.id.simLapButton);


        SharedPreferences prefs = getSharedPreferences(LAP_PREFS_NAME, Context.MODE_PRIVATE);

        if (secretMultInput != null) {
            secretMultInput.setText(String.valueOf(secret_mult));
            secretMultInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    try {
                        secret_mult = Double.parseDouble(s.toString());
                    } catch (NumberFormatException e) {
                        // ignore invalid input
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (simulateLapButton != null) {
            simulateLapButton.setOnClickListener(v -> {
                lap_trigger();
                Toast.makeText(this, "Simulated Lap " + lap_number, Toast.LENGTH_SHORT).show();
            });
        }

        // Lap Timing Setup
        lap1Input = findViewById(R.id.lap1_input);
        lap2Input = findViewById(R.id.lap2_input);
        lap3Input = findViewById(R.id.lap3_input);
        lap4Input = findViewById(R.id.lap4_input);

        setupLapInput(lap1Input, "lap1", 0);
        setupLapInput(lap2Input, "lap2", 1);
        setupLapInput(lap3Input, "lap3", 2);
        setupLapInput(lap4Input, "lap4", 3);

        if (ResetLapButton != null) {
            ResetLapButton.setOnClickListener(v -> {
                lap_timestamp = 0;
                lap_number = 0;
                stopRecording();
                startLatitude = 0;
                startLongitude = 0;
                hasLeftStartArea = false;
                if (hasLeftStartAreaIndicator != null) {
                    hasLeftStartAreaIndicator.setText("NO");
                    hasLeftStartAreaIndicator.setTextColor(Color.RED);
                }
                TimerLabel.setText("Press");
                LapOffsetTimer.setText("+/- None");
                LapOffsetTimer.setTextColor(Color.parseColor("#9C27B0"));
                Toast.makeText(this, "Laps Reset", Toast.LENGTH_SHORT).show();
            });
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
                
                // Update Lap Timer UI
                if (TimerNumber != null) {
                    if (lap_timestamp == 0 || race_start_timestamp == 0) {
                        TimerNumber.setText("0:00");
                    } else {
                        long elapsed = (race_start_timestamp + ((35 * 60) + 10) * 1000) - System.currentTimeMillis();
                        int seconds = (int) (elapsed / 1000) % 60;
                        int minutes = (int) ((elapsed / (1000 * 60)));
                        TimerNumber.setText(String.format(Locale.ENGLISH, "%d:%02d", minutes, seconds));
                    }
                }
                if (LapNumber != null) {
                    LapNumber.setText(String.valueOf(lap_number));
                }

                // Update Ideal Start Countdown
                if (IdealStartCountdown != null && race_start_timestamp != 0 && lap_number > 0 && lap_number <= 4) {
                    double ideal_next_start = race_start_timestamp;
                    for (int i = 0; i < lap_number && i < expectedLapTimes.length; i++) {
                        ideal_next_start += expectedLapTimes[i] * 1000;
                    }
                    //old code for countdown
                    long remainingMillis = (long) ideal_next_start - System.currentTimeMillis();

                    boolean isNegative = remainingMillis < 0;
                    long absMillis = Math.abs(remainingMillis);
                    int seconds = (int) (absMillis / 1000) % 60;
                    int minutes = (int) (absMillis / (1000 * 60));

                    String formatted = String.format(Locale.ENGLISH, "%s%d:%02d", isNegative ? "-" : "", minutes, seconds);
                    IdealStartCountdown.setText(formatted);
                    IdealStartCountdown.setTextColor(isNegative ? Color.RED : Color.GREEN);
                } else if (IdealStartCountdown != null) {
                    IdealStartCountdown.setText("0:00");
                    IdealStartCountdown.setTextColor(Color.GREEN);
                }

                if (race_start_timestamp != 0 && System.currentTimeMillis() > RACE_LENGTH + race_start_timestamp) {
                    lap_number = -1;
                    lap_trigger();
                }

                log_file_write_handler.postDelayed(this, log_timing);
            }
        }, log_timing);

        //define a location listener
        locationListener = location -> {
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            gps_speed = location.getSpeed() * 2.23693629;
            double time_millis = location.getTime();
            updateDataFrame("gps_time", time_millis);
            Latitude.setText(String.format(Locale.ENGLISH, "%.4f", lat));
            updateDataFrame("gps_latitude", String.format(Locale.ENGLISH, "%.4f", lat));
            Longitude.setText(String.format(Locale.ENGLISH, "%.4f", lon));
            updateDataFrame("gps_longitude", String.format(Locale.ENGLISH, "%.4f", lon));
            GPSSpeed.setText(String.format(Locale.ENGLISH, "%.2f", gps_speed));
            updateDataFrame("gps_speed", gps_speed);

            // GPS Lap Counter Logic
            if (race_start_timestamp != 0) {
                if (startLatitude == 0 && startLongitude == 0) {
                    startLatitude = lat;
                    startLongitude = lon;
                    hasLeftStartArea = false;
                    if (hasLeftStartAreaIndicator != null) {
                        hasLeftStartAreaIndicator.setText("NO");
                        hasLeftStartAreaIndicator.setTextColor(Color.RED);
                    }
                } else {
                    float[] results = new float[1];
                    Location.distanceBetween(startLatitude, startLongitude, lat, lon, results);
                    float distanceInMeters = results[0];
                    float distanceInFeet = distanceInMeters * 3.28084f;

                    if (!hasLeftStartArea && distanceInFeet > 100) {
                        hasLeftStartArea = true;
                        if (hasLeftStartAreaIndicator != null) {
                            hasLeftStartAreaIndicator.setText("YES");
                            hasLeftStartAreaIndicator.setTextColor(Color.GREEN);
                        }
                    } else if (hasLeftStartArea && distanceInFeet < 20) {
                        lap_trigger();
                        hasLeftStartArea = false;
                        if (hasLeftStartAreaIndicator != null) {
                            hasLeftStartAreaIndicator.setText("NO");
                            hasLeftStartAreaIndicator.setTextColor(Color.RED);
                        }
                    }
                }
            }

            if ((System.currentTimeMillis() - ecu_speed_timestamp) > 500) {
                SpeedLabel.setText("Speed (GPS)");
                handleSpeed(gps_speed);
            }
        };

        startGPSTracking();

        RecordingButton.setOnClickListener(v -> temp_logging_start());

        SpeedNumber2.setOnClickListener(v -> {
            if (secret_mult == 1.0) {
                SpeedNumber2.setTextColor(Color.BLUE);
                secret_mult = 0.5;
            }
            else {
                secret_mult = 1.0;
                SpeedNumber2.setTextColor(Color.GREEN);
            }
        });

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

        IPAddress.setText("http://" + getWifiIpAddress() + ":8080");

        startBluetoothAutoConnect();
        listPairedDevices();
    }

    private void setupLapInput(EditText editText, String key, int index) {
        if (editText == null) return;

        SharedPreferences prefs = getSharedPreferences(LAP_PREFS_NAME, Context.MODE_PRIVATE);
        String savedValue = prefs.getString(key, "517");
        editText.setText(savedValue);

        try {
            expectedLapTimes[index] = Double.parseDouble(savedValue);
        } catch (NumberFormatException ignored) {}

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String val = s.toString();
                prefs.edit().putString(key, val).apply();
                try {
                    expectedLapTimes[index] = Double.parseDouble(val);
                } catch (NumberFormatException e) {
                    // ignore invalid input
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void startBluetoothAutoConnect() {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkDefaultBluetoothDevice();
                handler.postDelayed(this, 100); // Check every 100ms for fast response
            }
        }, 100);
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

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        new Thread(() -> {
            BluetoothSocket tempSocket = null;
            try {
                tempSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);

                // Watchdog to enforce a short timeout (1.5s) to allow rapid retries if device is off
                final BluetoothSocket socketToClose = tempSocket;
                Thread watchdog = new Thread(() -> {
                    try {
                        Thread.sleep(1500);
                        if (isBluetoothConnecting) {
                            try { socketToClose.close(); } catch (IOException ignored) {}
                        }
                    } catch (InterruptedException ignored) {}
                });
                watchdog.start();

                tempSocket.connect();
                watchdog.interrupt();

                synchronized (this) {
                    if (bluetoothSocket != null) {
                        try { bluetoothSocket.close(); } catch (IOException ignored) {}
                    }
                    bluetoothSocket = tempSocket;
                }

                isBluetoothConnecting = false;
                runOnUiThread(() -> {
                    try {
                        Toast.makeText(this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show();
                    } catch (SecurityException e) {
                        Log.e("SSE", "Permission denied for getName", e);
                    }
                });
                startBluetoothReader();
            } catch (IOException e) {
                isBluetoothConnecting = false;
                if (tempSocket != null) {
                    try { tempSocket.close(); } catch (IOException ignored) {}
                }
                Log.d("SSE", "Bluetooth connection attempt failed");
            }
        }).start();
    }

    public boolean isBluetoothConnected() {
        return bluetoothSocket != null && bluetoothSocket.isConnected();
    }

    public boolean isBluetoothConnecting() {
        return isBluetoothConnecting;
    }

    private void startBluetoothReader() {
        new Thread(() -> {
            BluetoothSocket currentSocket = bluetoothSocket;
            try (InputStream inputStream = currentSocket.getInputStream()) {
                byte[] buffer = new byte[1024];
                int bytes;
                while (currentSocket.isConnected() && currentSocket == bluetoothSocket) {
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
                    if (bluetoothSocket == currentSocket) {
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

        if (!pairedDevices.isEmpty()) {
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
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideSystemUI();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE) {
            boolean bluetoothGranted = false;
            boolean locationGranted = false;
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.BLUETOOTH_CONNECT) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    bluetoothGranted = true;
                }
                if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    locationGranted = true;
                }
            }
            if (bluetoothGranted) {
                listPairedDevices();
            }
            if (locationGranted) {
                startGPSTracking();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording();
        synchronized (this) {
            if (bluetoothSocket != null) {
                try {
                    bluetoothSocket.close();
                } catch (IOException e) {
                    Log.e("SSE", "Error closing bluetooth socket on destroy", e);
                }
                bluetoothSocket = null;
            }
        }
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
            pushAlert("On Run Error Called", Arrays.toString(e.getStackTrace()), "yikes");
        }
    }

    private void startRecording() {
        if (race_start_timestamp == 0) {
            create_csv();
            if (RecordingButton != null) RecordingButton.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
        }
    }

    private void stopRecording() {
        if (race_start_timestamp != 0) {
            race_start_timestamp = 0;
            if (RecordingButton != null) RecordingButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#541414")));
        }
    }

    private void temp_logging_start() {
        try {
            if (race_start_timestamp == 0) {
                startRecording();
            }
            else {
                stopRecording();
            }
        } catch (Exception e) {
            if (RecordingButton != null) RecordingButton.setBackgroundTintList(ColorStateList.valueOf(Color.BLUE));
            if (e.getMessage() != null) Log.e("SSE", e.getMessage());
        }
    }

    private void log_message_received(int messageID) {
        messageCounts.merge(messageID, 1, Integer::sum);
    }

    private void lap_trigger() {
        lap_timestamp = System.currentTimeMillis();
        lap_number++;
        // Cycle logic: 0 -> 1 -> 2 -> 3 -> 4 -> 0

        if (lap_number == 1) {
            // Laps 1-4: Start recording
            startRecording();
            LapNumber.setText(String.valueOf(lap_number));
            TimerLabel.setText("Time");
            LapOffsetTimer.setText("+/- None");
            LapOffsetTimer.setTextColor(Color.parseColor("#9C27B0"));
        } else if (lap_number == 0) {
            // Lap 0 (Waiting state): Stop recording
            stopRecording();
            race_start_timestamp = 0;
            TimerLabel.setText("Press");
            LapOffsetTimer.setText("+/- None");
            LapOffsetTimer.setTextColor(Color.parseColor("#9C27B0"));
        }
        else {
            //we have the expected time of each lap
            //we have the current lap number
            //we have a timestamp of when this lap started

            //we want the expected lap duration - the (lap timestamp + ideal lap end timestamp)
            //the ultimate goal here is to get the amount faster/slower than the expected lap time the driver needs to be to stay on pace
            // (have the next lap begin when it's expected to begin, i.e. x seconds after the race started, calculated by adding up the expected lap times)

            double expected_lap_end_time = race_start_timestamp;
            for (int i = 0; i < lap_number && i < expectedLapTimes.length; i++) {
                expected_lap_end_time += expectedLapTimes[i] * 1000;
            }
            //double lap_offset_time = (expectedLapTimes[lap_number - 1] * 1000 - (lap_timestamp - expected_lap_end_time)) / 1000;
            double lap_offset_time = (expected_lap_end_time - (lap_timestamp + (expectedLapTimes[Math.min(lap_number, expectedLapTimes.length) - 1] * 1000))) / 1000;
            double absSeconds = Math.abs(lap_offset_time);
            int minutes = (int) (absSeconds / 60);
            int seconds = (int) (absSeconds % 60);

            String timeFormatted = String.format(Locale.ENGLISH, "%d:%02d", minutes, seconds);

            if (lap_offset_time > 0) {
                LapOffsetTimer.setText(String.format(Locale.ENGLISH, "+%s", timeFormatted));
                LapOffsetTimer.setTextColor(Color.YELLOW);
            }
            else {
                LapOffsetTimer.setText(String.format(Locale.ENGLISH, "-%s", timeFormatted));
                LapOffsetTimer.setTextColor(Color.RED);
            }
        }
    }

    private void handle_complete_message(Message message) {
        try {
            currentMessageBuilder = new StringMessageBuilder();
            if (message != null) {
                log_message_received(message.messageID);
                switch (message.messageType){
                    case 1:
                        switch (message.messageID) {
                            //signal from the lap button being pressed
                            case 0x2:
                                if (race_start_timestamp == 0) {
                                    lap_trigger();
                                }
                                break;
                            case 0x640:
                                int rpm = message.getContentVariable(0, 16);
                                RpmNumber.setText(String.format(Locale.ENGLISH, "%d", rpm));
                                RpmNumber2.setText(String.format(Locale.ENGLISH, "%d", rpm));
                                EngineIndicator.setBackgroundTintList(ColorStateList.valueOf( rpm > 1600 ? Color.GREEN :Color.RED));
                                StarterIndicator.setBackgroundTintList(ColorStateList.valueOf( rpm > 100 && rpm < 1500 ? Color.GREEN :Color.RED));
                                int throttlePosition = message.getContentVariable(48, 16) * 10;
                                ThrottlePositionBar.setProgress(throttlePosition);
                                updateDataFrame("throttle_position", String.format(Locale.ENGLISH, "%d", throttlePosition));
                                break;
                            case 0x641:
                                int fuelPressure = message.getContentVariable(32, 16) / 10;
                                if (FuelPressure != null) FuelPressure.setText(String.format(Locale.ENGLISH, "%d kPa", fuelPressure));
                                updateDataFrame("fuel_pressure", String.format("%d", fuelPressure));
                                int fuelInjectorTiming = message.getContentVariable(48, 8);
                                updateDataFrame("fuel_injector_timing", String.format("%d", fuelInjectorTiming));
                                int engineEfficiency = message.getContentVariable(56, 8);
                                updateDataFrame("engine_efficiency", String.format("%d", engineEfficiency));
                                break;
                            case 0x642:
                                int engineLoad = message.getContentVariable(16, 16);
                                updateDataFrame("engine_load", String.format("%d", engineLoad));
                                break;
                            case 0x648:
                                //get the wheel speed from the back right wheel (what it's mapped to in the ECU)
                                double ecu_speed = (message.getContentVariable(48, 16) * 0.06213712 * secret_mult);
                                updateDataFrame("ecu_speed", String.format("%.1f", ecu_speed));
                                SpeedLabel.setText("Speed (ECU)");
                                ecu_speed_timestamp = System.currentTimeMillis();
                                handleSpeed(ecu_speed);
                                break;
                            case 0x649:
                                //I have co-opted the coolant_temp label for the ECU fuel used metric, fix the labels eventually
                                double coolant_temp = message.getContentVariable(48, 16) / 100.0;
                                CoolantTemperature.setText(String.format(Locale.ENGLISH, "%.3f L", coolant_temp));
                                int engineOilTemp = message.getContentVariable(8, 8) - 40;
                                updateDataFrame("oil_temp", String.format("%d", engineOilTemp));
                                float voltage = message.getContentVariable(40, 8) / (float) 10;
                                VoltageNumber.setText(String.format(Locale.ENGLISH, "%.1f V", voltage));
                                updateDataFrame("battery_voltage", String.format("%.1f", voltage));
                                if (voltage < 10.5) {
                                    if (warningObjects[5] != null) warningObjects[5].timestamp = System.currentTimeMillis();
                                }
                                break;
                            case 0x64c:
                                for (int i = 0; i < 9 && i < warningObjects.length; i++) {
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
                        }
                        break;
                    case 2:
                        switch (message.messageID) {

                            // message 1: structure: data 7 - data 6 (duty cycle), data 5 - data 4 (current), data 3 - data 0 (RPMs)
                            case 0x09: // CAN Packet Message 1: RPM, Total Current *10

                                int RPM = message.getContentVariable(32, 32); // 4 byte offset, 4 byte length
                                int current = message.getContentVariable(16, 16) / 10; // 2 byte offset, 2 byte length

                                // calculate wheel speed
                                int wheel_speed = 0; // (RPM * Tire Diameter) / Total Gear Ratio

                                // write screen logic
                                break;
                            case 0x0F: // CAN Packet Message 3: Total Watt-Hours, Total-Regen Hours

                                double regen_hrs = message.getContentVariable(0, 32) / 1000.0; // 0 byte offset, 4 byte length
                                double watt_hrs = message.getContentVariable(32, 32) / 1000.0; // 4 byte offset, 4 byte length

                                // write screen logic
                                break;
                            case 0x10: // CAN Packet Message 4: MOSFET Temp, Motor Temp

                                double motor_temp = message.getContentVariable(32, 16) / 10.0; // presumed Celsius
                                double MOSFET_TEMP = message.getContentVariable(48, 16) / 10.0; // presumed Celsius

                                // write screen logic here
                                break;
                        }
                        break;
                }

                handle_warnings();
            }
        } catch (Exception e) {
            Log.e("SSE", "Complete message error", e);
        }
    }

    public void handleSpeed(double vehicle_speed) {
        SpeedNumber.setText(String.format(Locale.ENGLISH, "%.1f", vehicle_speed));
        SpeedNumber2.setText(String.format(Locale.ENGLISH, "%.1f", vehicle_speed));
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

    private void updateDataFrame(String key, Object value) {
        data_frame.put(key, value);
        data_frame_timestamps.put(key, System.currentTimeMillis());
    }

    private void create_csv() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd__HH-mm-ss", Locale.getDefault());
        String filename = sdf.format(new Date()) + ".csv";
        File directory = getFilesDir();
        this.current_file = new File(directory, filename);
        this.row = 0;
        this.distance = 0;
        this.race_start_timestamp = System.currentTimeMillis();
        this.startLatitude = 0;
        this.startLongitude = 0;
        this.hasLeftStartArea = false;
        this.data_frame.clear();
        this.data_frame_timestamps.clear();
        this.messageCounts.clear();

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

        for (Map.Entry<Integer, Integer> entry : messageCounts.entrySet()) {
            updateDataFrame(String.format(Locale.ENGLISH, "0x%X", entry.getKey()), entry.getValue());
        }
        messageCounts.clear();

        updateDataFrame("unix_timestamp", System.currentTimeMillis());
        updateDataFrame("lap_unix_timestamp", System.currentTimeMillis() - this.lap_timestamp);
        updateDataFrame("row", row++);
        updateDataFrame("lap_number", lap_number);
        updateDataFrame("distance", distance);

        try (FileWriter writer = new FileWriter(this.current_file, true)) {
            List<String> rowValues = new ArrayList<>();
            long currentTime = System.currentTimeMillis();
            for (String header : csv_headers) {
                Object value = this.data_frame.get(header);
                Long timestamp = this.data_frame_timestamps.get(header);
                if (value != null && timestamp != null && (currentTime - timestamp) < 1200) {
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
            "row", "lap_number", "unix_timestamp", "lap_unix_timestamp", "distance", "lap_distance", "gps_time",
            "gps_latitude", "gps_longitude", "gps_speed", "ecu_speed", "throttle_position", "engine_efficiency",
            "oil_temp", "engine_load", "wheel_speed_ecu", "wheel_speed_arduino", "fuel_injector_timing",
            "acceleration_lateral", "acceleration_longitudinal", "acceleration_vertical", "fuel_pressure",
            "battery_voltage", "message_rec_bits",
            "0x640", "0x641", "0x642", "0x649", "0x64C", "0x460", "0x118"
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
                    if (remainingBytes == 2 && (char) b == '2'){
                        messageType = 2;
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

        //convert the string builder to a message object
        public Message getMessage() {
            String result = stringBuilder.toString();
            String[] hexBytes = result.trim().split(" ");
            try {
                this.messageID = this.messageID | Integer.parseInt(hexBytes[0], 16);
                for (int i = 1; i < hexBytes.length; i++) {
                    this.messageContent = this.messageContent << 8;
                    this.messageContent = this.messageContent | Integer.parseInt(hexBytes[i], 16);
                }
            } catch (Exception e) {
                Log.i("SSE", e.getMessage());
            }
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

    private void startGPSTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 90, 0, locationListener);
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
