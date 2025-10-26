//TODO:
//8. switch all errors over to logcat logs
//3. write data to a file
// make the page auto-refresh

package com.example.a7_0project;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.example.http_file_server.HttpServerService;

public class MainActivity extends AppCompatActivity {

    private static final String ACTION_USB_PERMISSION = "com.android.example.a7_0project.USB_PERMISSION";

    private UsbManager usbManager;

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
    private TextView label3;
    private TextView label4;
    private Button serial_button;
    private StringMessageBuilder currentMessageBuilder;
    private TextView RpmNumber;
    private TextView SpeedLabel;
    private TextView SpeedNumber;
    private ProgressBar RedlineIndicator;
    private ProgressBar ThrottlePositionBar;
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

    private LinearLayout DriverView;
    private LinearLayout StatsView;

    private SerialInputOutputManager usbIoManager;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private int LOCATION_REQUEST_CODE = 100;
    private FileOutputStream logFile;
    private TextView ErrorNotif;
    private long errorTimestamp;
    private TextView MessageCount;
    private int messageCounter;
    public static boolean threadLock = false;

    private final WarningBundle[] warningObjects = new WarningBundle[9];

    private StringBuilder stringBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //start the HTTP server to show internal files for download
        Intent intent = new Intent(this, HttpServerService.class);
        startService(intent);

        //hide the home button, notification bar, and that weird bar with the app name
        hideSystemUI();

        //no clue, maybe show what's in layout.xml?
        setContentView(R.layout.layout);

        //get references to all the UI elements defined in layout.xml
        label1 = findViewById(R.id.label1);
        label2 = findViewById(R.id.label2);
        label3 = findViewById(R.id.label3);
        label4 = findViewById(R.id.label4);
        serial_button = findViewById(R.id.serial_button);
        ErrorNotif = findViewById(R.id.errorNotif);
        MessageCount = findViewById(R.id.messageCount);
        RpmNumber = findViewById(R.id.rpmNumber);
        SpeedLabel = findViewById(R.id.speedLabel);
        SpeedNumber = findViewById(R.id.speedNumber);
        RedlineIndicator = findViewById(R.id.redlineIndicator);
        ThrottlePositionBar = findViewById(R.id.throttlePositionBar);
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
        DriverView = findViewById(R.id.driver_view);
        StatsView = findViewById(R.id.stats_view);

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

        //request fine location permission

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST_CODE);

        //location manager object to start and stop tracking location
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        //define a location listener (what code to run when the location updates)
        locationListener = location -> {
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            double speed = location.getSpeed();
            double time_millis = location.getTime();
            data_frame.put("gps_time", time_millis);
            Latitude.setText(String.format(Locale.ENGLISH, "%.4f", lat));
            data_frame.put("gps_latitude", String.format(Locale.ENGLISH, "%.4f", lat));
            Longitude.setText(String.format(Locale.ENGLISH, "%.4f", lon));
            data_frame.put("gps_longitude", String.format(Locale.ENGLISH, "%.4f", lon));
            GPSSpeed.setText(String.format(Locale.ENGLISH, "%.2f", speed));
            data_frame.put("gps_speed", speed);

            IPAddress.setText("http://" + getWifiIpAddress() + ":8080");
            label2.setText("Lat: " + lat + "\nLon: " + lon);

            //save the current data to the file
            if (race_start_timestamp != 0) {
                append_csv();
            }
            //clear the data object
            data_frame.clear();
        };

        //serial_button.setOnClickListener(v -> startGPSTracking());
        BurnOrCoast.setOnClickListener(v -> toggleStatsView());
        startGPSTracking();

        //TODO: make starter button lap button
        StarterIndicator.setOnClickListener(v -> temp_logging_start());
        //add more data from ecu and stuff
        //test on phone
        //update documentation to be clear about data formats???

        // Make the USB manager object to handle USB connections
        this.usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

//        Handler messageCountHandler = new Handler();
//        messageCountHandler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                MessageCount.setText(String.format(Locale.ENGLISH, "%d", messageCounter));
//                messageCounter = 0;
//                messageCountHandler.postDelayed(this, 1000);
//            }
//        }, 1000);

        label1.setText(getWifiIpAddress());
        IPAddress.setText("http://" + getWifiIpAddress() + ":8080");

        Intent this_intent = getIntent();
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(this_intent.getAction())) {
            Log.i("SSE", "Activity started by USB device attachment.");
            // Your existing USB connection logic (e.g., auto_reconnect())
            // should already be handling this if it's called in onCreate.
        }

        //start the auto_reconnect system which will handle setting up the initial connection
        auto_reconnect();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // Important to update the Activity's intent
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            // USB device was attached while Activity was running
            // You might want to re-initiate the connection logic here
            // or ensure your existing connection logic handles this gracefully.
            Log.i("SSE", "USB device attached while Activity is running.");
            // Potentially call your auto_reconnect() or connect() method,
            // but be careful about creating multiple connections.
            // You might need to check if a connection is already active.
            if (usbIoManager == null || usbIoManager.getState() == SerialInputOutputManager.State.STOPPED) {
                auto_reconnect(); // Or directly call connect() if appropriate
            }
        }
    }

    //immediately hide UI whenever anything happens
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
        // Register the receiver to listen for USB permission responses
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        // Add the RECEIVER_NOT_EXPORTED flag as the third argument
        registerReceiver(usbPermissionReceiver, filter, RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the receiver when the app is paused
        unregisterReceiver(usbPermissionReceiver);
    }

    // Add this BroadcastReceiver to your MainActivity class members
    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                // We are using a Handler, so we don't need to lock (synchronized)
                // The broadcast is received, check if permission was granted
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    Log.i("SSE", "USB permission GRANTED by user.");
                    // Permission granted. Now, find the device and try to connect.
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null) {
                        // Call a method that specifically handles the connection logic
                        // This separates the connection logic from the permission request
                        try_to_connect_after_permission(device);
                    }
                } else {
                    Log.w("SSE", "USB permission DENIED by user.");
                    pushAlert("USB Permission", "Permission denied for USB device. Cannot connect.", "OK");
                }
            }
        }
    };

    /**
     * If there is no USB connection, keep retrying every DELAY ms to establish one.
     */
    private void auto_reconnect() {
        label1.setText("Attempting autoreconnect...");
        final Handler handler = new Handler();
        final int delay = 1000; // Increased delay to 1 second to be less aggressive

        handler.postDelayed(new Runnable() {
            public void run() {
                Log.i("SSE", "Autoreconnect handler running...");
                label2.setText("Checking for USB device...");

                // Check if already connected
                if (usbIoManager != null && usbIoManager.getState() != SerialInputOutputManager.State.STOPPED) {
                    Log.i("SSE", "Already connected. Stopping autoreconnect.");
                    label1.setText("Connected");
                    return; // Stop the handler
                }

                if (usbManager.getDeviceList().isEmpty()) {
                    Log.i("SSE", "No USB devices found. Retrying...");
                    label2.setText("No device found, trying again...");
                    handler.postDelayed(this, delay); // Retry
                    return;
                }

                // Get the first device
                UsbDevice device = usbManager.getDeviceList().values().iterator().next();

                if (usbManager.hasPermission(device)) {
                    Log.i("SSE", "Permission already granted. Connecting...");
                    label2.setText("Permission OK. Connecting...");
                    try_to_connect_after_permission(device); // Connect directly
                } else {
                    Log.i("SSE", "Permission not granted. Requesting...");
                    label2.setText("Requesting USB permission...");
                    // Request permission. The BroadcastReceiver will handle the result.
                    PendingIntent permissionIntent = PendingIntent.getBroadcast(
                            MainActivity.this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
                    );
                    usbManager.requestPermission(device, permissionIntent);
                    // Do NOT try to connect here. Wait for the receiver.
                }

                // The handler will run again to check status, but will exit at the top if connected.
                handler.postDelayed(this, delay);
            }
        }, delay);
    }

    /**
     * The main connection logic. Should only be called AFTER permission is granted.
     *
     * @param device The UsbDevice to connect to.
     */
    private void try_to_connect_after_permission(UsbDevice device) {
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            pushAlert("Error", "No USB serial drivers available.", "OK");
            return;
        }

        // Find the driver for our specific device
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
            Log.e("SSE", "usbManager.openDevice() failed. This can happen if the device is disconnected.");
            // We don't push an alert here because auto_reconnect will try again.
            return;
        }

        label4.setText("Connection successful!");

        UsbSerialPort port = driver.getPorts().get(0); // Most devices have just one port (port 0)

        try {
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            //port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
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
//                if (threadLock) {
//                    pushAlert("ERROR", "Threadlock violated", "darn");
//                }
                //threadLock = true;
                try {
                    //Log.v("SSE", "New data encountered");
                    for (byte b : data) {
                        MessageBuilderState msgb_state = currentMessageBuilder.add(b);
                        if (msgb_state == MessageBuilderState.COMPLETE) {
                            Log.v("mes", currentMessageBuilder.stringBuilder.toString());
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
        if (this.race_start_timestamp == 0) {
            create_csv();
            SpeedLabel.setTextColor(Color.YELLOW);
            this.race_start_timestamp = System.currentTimeMillis();
        }
        else {
            SpeedLabel.setTextColor(Color.GREEN);
            this.race_start_timestamp = 0;
        }
    }

    private void temp_lap_button() {
        this.lap_number++;
    }

    private void handle_complete_message(Message message) {
        try {
            //serialButton.setBackgroundColor(Color.GREEN);
            currentMessageBuilder = new StringMessageBuilder();
            if (message == null) {
                pushAlert("bad news", "someone (not naming names) (john) Called getMessage on an unfinished message object", "oopsies");
            } else {
                switch (message.messageID) {
                    case 0x640:
                        int rpm = message.getContentVariable(0, 16);
                        RpmNumber.setText(String.format(Locale.ENGLISH, "%d", rpm));
                        //engine indicator enabled if rpm is above 1600
                        EngineIndicator.setBackgroundTintList(ColorStateList.valueOf( rpm > 1600 ? Color.GREEN :Color.RED));
                        //starter indicator enabled if rpm is between 100 and 1500
                        StarterIndicator.setBackgroundTintList(ColorStateList.valueOf( rpm > 100 && rpm < 1500 ? Color.GREEN :Color.RED));
                        break;
                    case 0x641:
                        //convert from 0.1kPa to 1 kPa
                        int fuelPressure = message.getContentVariable(32, 16) / 10;
                        FuelPressure.setText(String.format(Locale.ENGLISH, "%d kPa", fuelPressure));
                        break;
                    case 0x649:
                        //convert from 0.1 volts to 1 volts
                        float voltage = message.getContentVariable(40, 8) / (float) 10;
                        VoltageNumber.setText(String.format(Locale.ENGLISH, "%.1f V", voltage));
                        if (voltage < 10.5) {
                            warningObjects[5].timestamp = System.currentTimeMillis();
                        }
                        break;
                    case 0x64c:
                        for (int i = 0; i < 9; i++) {
                            if (message.getContentVariable(40 + i, 1) == 1) {
                                //warningObjects[i].warningLabel.setTextColor(Color.BLUE);
                                if (warningObjects[i] != null) {
                                    warningObjects[i].timestamp = System.currentTimeMillis();
                                }
                            }
                        }
                        break;
                    case 0x460:
                        //filter for the right message based on this chart
                        //https://www.manualslib.com/manual/1417922/Motec-Ltc.html?page=39#manual
                        if (message.getContentVariable(0, 8) == 0) {
                            //convert from 0.001 lambda to 1 lambda
                            double lambda = message.getContentVariable(8, 16) / 1000.0;
                            LambdaNumber.setText(String.format(Locale.ENGLISH, "%4.3f", lambda));
                        }
                        break;
                    case 0x118:
                        //convert from 1 kmh to mph
                        double vehicle_speed = (message.getContentVariable(16, 8) * 0.6213712);
                        SpeedNumber.setText(String.format(Locale.ENGLISH, "%.1f", vehicle_speed));
                        RedlineIndicator.setProgress((int) (vehicle_speed * 10));
                        RedlineIndicator.setProgressTintList(ColorStateList.valueOf(vehicle_speed > 26 || vehicle_speed < 10 ? Color.RED : Color.YELLOW));
                        if (vehicle_speed < 24 || vehicle_speed > 13) {
                            RedlineIndicator.setProgressTintList(ColorStateList.valueOf(Color.GREEN));
                        }
                        if (vehicle_speed > 24) {
                            BurnOrCoast.setText("Coast!");
                            BurnOrCoast.setTextColor(Color.GREEN);
                        }
                        if (vehicle_speed < 13) {
                            BurnOrCoast.setText("Burn!");
                            BurnOrCoast.setTextColor(Color.RED);
                        }
                        int throttlePosition = message.getContentVariable(8, 8);
                        ThrottlePositionBar.setProgress(throttlePosition);
                        int coolant_temp = message.getContentVariable(24, 8);
                        CoolantTemperature.setText(String.format(Locale.ENGLISH, "%d C", coolant_temp));
                        break;
                }

                handle_warnings();
            }
        }
        catch (Exception e) {
            Log.e("SSE", "Complete message error", e);
        }
    }

    public void handle_warnings() {
        for (WarningBundle bundle : warningObjects) {
            //make sure this is a warning we can display
            if (bundle != null) {
                boolean active = (System.currentTimeMillis() - bundle.timestamp) < 6000;
                boolean flashedOn = ((System.currentTimeMillis() - bundle.timestamp) % 1000 < 650) && active;
                //CoolantTemperature.setText(String.format(Locale.ENGLISH, "%d", (System.currentTimeMillis() - bundle.timestamp)));
                bundle.warningImage.setAlpha((float) (flashedOn ? 1.0 : 0.2));
                bundle.warningLabel.setAlpha((float) (flashedOn ? 1.0 : 0.2));
                bundle.number.setTextColor(active ? Color.RED : Color.GREEN);
                bundle.label.setTextColor(active ? Color.RED : Color.GREEN);

//                bundle.warningImage.setForegroundTintList(ColorStateList.valueOf(flashedOn ? Color.RED : Color.GREEN));
//                bundle.warningLabel.setTextColor(flashedOn ? Color.RED : Color.GREEN);
            }
        }
    }

    private class WarningBundle {
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

    public void showError() {
        ErrorNotif.setAlpha(1);
        errorTimestamp = System.currentTimeMillis();
        Handler errorExpire = new Handler();
        errorExpire.postDelayed(() -> {
            if (System.currentTimeMillis() - errorTimestamp > 1000) {
                ErrorNotif.setAlpha(0.4F);
            }
        }, 1005);
    }

    public void pushAlert(String title, String message, String buttonText) {
        // Create the object of AlertDialog Builder class
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        // Set the message show for the Alert time
        builder.setMessage(message);
        // Set Alert Title
        builder.setTitle(title);
        builder.setCancelable(true);
        // Set the Negative button with No name Lambda
        // OnClickListener method is use of DialogInterface interface.
        builder.setNegativeButton(buttonText, (dialog, which) -> {
            // If user click no then dialog box is canceled.
            dialog.cancel();
        });
        // Create the Alert dialog
        AlertDialog alertDialog = builder.create();
        // Show the Alert Dialog box
        alertDialog.show();
    }

    /**
     * Creates a new CSV file in the app's internal storage directory,
     * named with the current timestamp, and writes the headers.
     */
    private void create_csv() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_at_HH-mm-ss", Locale.getDefault());
        String filename = sdf.format(new Date()) + ".csv";

        File directory = getFilesDir();
        this.current_file = new File(directory, filename);

        //use this opportunity to set/reset the persistent variables like lap_number
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

            Log.i("CSV_Creation", "Successfully created and wrote headers to " + this.current_file.getName());
        } catch (IOException e) {
            Log.e("CSV_Creation", "Error writing to CSV file", e);
            // Inform the user that file creation failed
            //pushAlert("File Error", "Could not create log file: " + e.getMessage(), "OK");
        }
    }

    /**
     * Appends the current state of telemetry data as a new row to a CSV file.
     * This method assumes create_csv has already been called to create the file and its headers.
     */
    private void append_csv() {
        // Find the most recently created file. This is a simple approach.
        // For long-running apps, you might want to store the fileName in a member variable.

        if (this.current_file == null) {
            Log.e("CSV_Append", "No CSV file found to append to. Call create_csv() first.");
            return;
        }

        //some data like timers need to be added right now as there isn't a better place
        data_frame.put("unix_timestamp", System.currentTimeMillis());
        data_frame.put("lap_unix_timestamp", System.currentTimeMillis() - this.lap_start_timestamp);
        data_frame.put("row", row++);

        //other persistent data like the lap count also needs to be added now
        data_frame.put("lap", lap_number);
        data_frame.put("distance", distance);


        // Use try-with-resources and pass 'true' to FileWriter to enable append mode.
        try (FileWriter writer = new FileWriter(this.current_file, true)) {
            // This list will hold the values in the correct order for the CSV row.
            List<String> rowValues = new ArrayList<>();

            // Iterate through the master list of headers. This defines the column order.
            for (String header : csv_headers) {
                // Get the value from our map.
                Object value = this.data_frame.get(header);

                // If the value exists, convert it to a string.
                // If it's null (never set), add an empty string to the CSV.
                if (value != null) {
                    if (value instanceof Double || value instanceof Float) {
                        rowValues.add(String.format(Locale.ENGLISH,"%.2f", value));
                    }
                    else {
                        rowValues.add(value.toString());
                    }
                } else {
                    rowValues.add(""); // Represents a "null" or missing value in the CSV
                }
            }

            // Join the ordered values with commas and write the line to the file.
            writer.write(String.join(",", rowValues));
            writer.write("\n"); // Add a newline for the next row
            //write it out NOW so that it doesn't get borked if it crashes or smth
            writer.flush();

        } catch (IOException e) {
            Log.e("CSV_Append", "Error appending data to CSV file", e);
        }
    }

    /**
     * NOTE: The actual sample time of any given data point here is potentially off by as much as 1 second
     * when compared to the timestamp for that row.
     * Most data from the ECU *should* be from relatively recently
     *
     All of these variables should have:
     - An Explanation
     - Units
     - Precision
     */
    public String[] csv_headers = {
            //row number for this record, incrementing number
            //no unit
            //perfectly precise
            "row",

            //unix timestamp
            //seconds
            //not precise to when values were actually recorded (read docs)
            "unix_timestamp",

            //seconds since the lap started
            //seconds
            //not precise to when values were actually recorded (read docs)
            "lap_unix_timestamp",

            //distance gone since the start of the race
            //meters?
            //TODO: figure out how to calculate this accurately
            //unknown precision
            "distance",

            //distance gone in the current lap
            //meters
            //unknown precision
            "lap_distance",

            //formatted timestamp from the GPS
            //formatted as HH.mm.ss (0 padded)
            //Very precise BUT not precise to when values were actually recorded (read docs)
            "gps_time",

            //gps latitude
            //decimal degrees
            // GPS Test app (likely as low as 10 feet, error should be relatively consistent?)
            "gps_latitude",

            //gps longitude
            //decimal degrees
            // GPS Test app
            "gps_longitude",

            //estimate of speed from the android phone gps
            //miles/hour (converted from meters/second)
            //test?? (compare to measured wheelspeed values)
            "gps_speed",

            //how pressed down the ECU thinks the throttle is
            //decimal percentage
            //likely accurate to within 1%
            "throttle_position",

            //interpolated value from the table named "Engine Efficiency"
            //https://www.hpacademy.com/forum/motec-m1-software-tutorial/show/engine-efficiency-2
            //only 256 possible values
            "engine_efficiency",

            //engine oil temperature
            //degrees celsius
            //ask engine team lol
            "oil_temp",

            //engine load from the ECU
            //1mg
            //unknown precision
            "engine_load",

            //wheel speed as measured by the ECU
            //0.1km/h
            //probably only accurate per one rotation bc we only have one magnet
            "wheel_speed_ecu",

            //wheel speed as directly measured by the arduino
            //0.1km/h
            //probably only accurate per one rotation bc we only have one magnet
            "wheel_speed_arduino",

            //"Fuel Injector Duty Cycle" from the ECU
            //expressed as a percentage (of what I do not know)
            //precise up to 1%
            "fuel_injector_timing",

            //car acceleration in latitude direction as measured by the ECU (probably null until we get a GPS?)
            //0.001G
            //unknown precision
            "acceleration_lateral",

            //car acceleration in longitude direction as measured by the ECU (probably null until we get a GPS?)
            //0.001G
            //unknown precision
            "acceleration_longitudinal",

            //car acceleration in vertical direction as measured by the ECU (probably null until we get a GPS?)
            //0.001G
            //unknown precision
            "acceleration_vertical",

            //fuel pressure as measured by the ECU
            //10kPa
            //only 256 possible values
            "fuel_pressure",

            //voltage of the battery as reported by the ECU
            //volts
            //xx.x
            "battery_voltage"
    };

    public class Message {
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
                default:
                    pushAlert("oopsies", "Unknown MessageBuilderState enum", "darn");
            }

            return state;
        }

        public Message getMessage() {
            String result = stringBuilder.toString();
            //label3.setText(result);
            String[] hexBytes = result.trim().split(" ");
            //label4.setText(Arrays.toString(hexBytes));
            //label1.setText(String.format(Locale.ENGLISH, "%d", hexBytes.length));
            try {
                this.messageID = this.messageID | Integer.parseInt(hexBytes[0], 16);
                for (int i = 1; i < hexBytes.length; i++) {
                    this.messageContent = this.messageContent << 8;
                    this.messageContent = this.messageContent | Integer.parseInt(hexBytes[i], 16);
                }
                //label2.setText(String.format(Locale.ENGLISH, "Good message found with ID %d", this.messageID));
            } catch (Exception e) {
                //label1.setText(e.toString());
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

    private void toggleStatsView() {
        IPAddress.setText("http://" + getWifiIpAddress() + ":8080");
        if (StatsView.getVisibility() == View.VISIBLE) {
            StatsView.setVisibility(View.GONE);
            DriverView.setVisibility(View.VISIBLE);
        } else {
            StatsView.setVisibility(View.VISIBLE);
            DriverView.setVisibility(View.GONE);
        }
    }

    private void startGPSTracking() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_REQUEST_CODE);
            return;
        }

        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000,  // min time interval (1 second)
                0,     // min distance change (0m)
                locationListener
        );
    }

    private String getWifiIpAddress() {
        Context context = getApplicationContext();
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        int ip = wm.getConnectionInfo().getIpAddress();
        if (ip != 0) {
            return String.format(Locale.ENGLISH,
                    "%d.%d.%d.%d",
                    (ip & 0xff),
                    (ip >> 8 & 0xff),
                    (ip >> 16 & 0xff),
                    (ip >> 24 & 0xff)
            );
        }
        return "No Local IP Address";
    }

    class BinaryMessageBuilder {
        int messageType;
        int messageID;
        long messageContent;
        int remainingBytes;

        private MessageBuilderState state;

        public BinaryMessageBuilder() {
            this.state = MessageBuilderState.WAITING;
        }

        MessageBuilderState add(byte b) {
            switch (this.state) {
                case WAITING:
                    if ((char) b == '[') {
                        this.state = MessageBuilderState.MESSAGE_TYPE;
                        remainingBytes = 4;
                    }
                    break;
                case MESSAGE_TYPE:
                    if (remainingBytes > 0) {
                        this.messageType = (this.messageType & b) << 8;
                        remainingBytes--;
                    } else if ((char) b == ']') {
                        this.state = MessageBuilderState.MESSAGE_ID;
                        this.remainingBytes = 4;
                    } else {
                        this.state = MessageBuilderState.WAITING;
                    }
                    break;
                case MESSAGE_ID:
                    if (remainingBytes > 0) {
                        this.messageID = (this.messageID & b) << 8;
                        remainingBytes--;
                    } else {
                        this.state = MessageBuilderState.MESSAGE_CONTENT;
                        remainingBytes = 8;
                    }
                    break;
                case MESSAGE_CONTENT:
                    if (remainingBytes > 0) {
                        this.messageContent = (this.messageContent & b) << 8;
                        remainingBytes--;
                    } else if ((char) b == '\n') {
                        this.state = MessageBuilderState.COMPLETE;
                    } else {
                        this.state = MessageBuilderState.WAITING;
                    }
                    break;
                case COMPLETE:
                    Log.e("SSE", "Called add on complete message!");
                    //throw new RuntimeException("Called add on complete message!\n");
            }

            return this.state;
        }

        public Message getMessage() {
            if (state == MessageBuilderState.COMPLETE) {
                return new Message(this.messageType, this.messageID, this.messageContent);
            } else {
                return null;
            }
        }
    }
}