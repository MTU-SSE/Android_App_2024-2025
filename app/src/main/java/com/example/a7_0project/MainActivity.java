//TODO:
//8. switch all errors over to logcat logs
//3. write data to a file
//4. grab gps coords
//1. talk with engine team about redline limits

package com.example.a7_0project;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
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
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String ACTION_USB_PERMISSION = "com.android.example.a7_0project.USB_PERMISSION";

    private UsbManager usbManager;

    private Button serialButton;
    private TextView label1;
    private TextView label2;
    private TextView label3;
    private TextView label4;
    private TextView label5;
    private StringMessageBuilder currentMessageBuilder;
    private TextView RpmNumber;
    private TextView SpeedNumber;
    private ProgressBar RedlineIndicator;
    private ProgressBar ThrottlePositionBar;
    private TextView VoltageNumber;
    private TextView FuelPressure;
    private TextView LambdaNumber;
    private TextView CoolantTemperature;

    private SerialInputOutputManager usbIoManager;
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

        setContentView(R.layout.layout);

        label1 = findViewById(R.id.label1);
        label2 = findViewById(R.id.label2);
        label3 = findViewById(R.id.label3);
        label4 = findViewById(R.id.label4);
        label5 = findViewById(R.id.label5);
        ErrorNotif = findViewById(R.id.errorNotif);
        MessageCount = findViewById(R.id.messageCount);
        RpmNumber = findViewById(R.id.rpmNumber);
        SpeedNumber = findViewById(R.id.speedNumber);
        RedlineIndicator = findViewById(R.id.redlineIndicator);
        ThrottlePositionBar = findViewById(R.id.throttlePositionBar);
        VoltageNumber = findViewById(R.id.voltageNumber);
        FuelPressure = findViewById(R.id.fuelPressureNumber);
        LambdaNumber = findViewById(R.id.lambdaNumber);
        CoolantTemperature = findViewById(R.id.coolantTempNumber);

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

        // Find all available USB drivers (for the attached devices?).
        this.usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        try {
            File appSpecificExternalDir = new File(getBaseContext().getExternalFilesDir(null), "HUD_log.txt");
            if (appSpecificExternalDir.createNewFile()) {
                logFile = new FileOutputStream(appSpecificExternalDir);
                Date date = new Date(System.currentTimeMillis());
                SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US);
                String rn = formatter.format(date);
                logFile.write(String.format("Beginning of log file %s:\n", rn).getBytes());
            }
        }
        catch (Exception e) {
            Log.e("SSE", "Shared file failed: ", e);
        }
//
        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            LocationListener locationListener = new MyLocationListener();
            //If the permission check fails
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("Permission check failed :(");
            }
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 2000, 5, locationListener);
        }
        catch (Exception e) {
            Log.e("SSE", "Location request failed: ", e);
        }

        Handler messageCountHandler = new Handler();
        messageCountHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                MessageCount.setText(String.format(Locale.ENGLISH, "%d", messageCounter));
                messageCounter = 0;
                messageCountHandler.postDelayed(this, 1000);
            }
        }, 1000);

        auto_reconnect();
    }

    private void auto_reconnect() {
        label1.setText("attempting autoreconnect");
        final Handler handler = new Handler();
        final int delay = 300; // 1000 milliseconds == 1 second

        handler.postDelayed(new Runnable() {
            public void run() {
                Log.i("SSE", "Handler ran!");
                try {
                    label2.setText("post delay ran");
                    if (usbManager.getDeviceList().isEmpty()) {
                        throw new Exception("No device found, trying again");
                    }
                    if (!usbManager.hasPermission(usbManager.getDeviceList().get(usbManager.getDeviceList().keySet().iterator().next()))) {
                        throw new Exception("Permission check failed, trying again");
                    } else {
                        if (!connect()) {
                            throw new Exception("Connect failed, trying again");
                        }
                    }
                } catch (Exception e) {
                    Log.i("SSE", e.toString());
                    handler.postDelayed(this, delay);
                }
            }
        }, delay);
    }

    private boolean connect() throws Exception {
        //list all devices in label 1
        label1.setText(usbManager.getDeviceList().toString());

        //find all usb drivers
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            serialButton.setBackgroundColor(Color.MAGENTA);
            pushAlert("MAJOR ERROR:", "No available USB drivers? This really should not happen...", "darn");
            return false;
        }
        //print the first found usb driver to label 2
        label2.setText(availableDrivers.get(0).toString());

        PendingIntent permissionIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        UsbDevice device = usbManager.getDeviceList().get(usbManager.getDeviceList().keySet().iterator().next());
        usbManager.requestPermission(device, permissionIntent);

        label3.setText(String.valueOf(usbManager.hasPermission(usbManager.getDeviceList().get(usbManager.getDeviceList().keySet().iterator().next()))));

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());
        if (connection == null) {
            serialButton.setBackgroundColor(Color.RED);
            return false;
        }

        label4.setText(String.valueOf(connection));

        UsbSerialPort port = driver.getPorts().get(0); // Most devices have just one port (port 0)

        port.open(connection);
        port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        SerialHandler serialHandler = new SerialHandler();
        this.usbIoManager = new SerialInputOutputManager(port, serialHandler);
        usbIoManager.start();

        return true;
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
            label5.setText(e.getMessage());
            auto_reconnect();
        }
    }

    private void handle_complete_message(Message message) {
        serialButton.setBackgroundColor(Color.GREEN);
        currentMessageBuilder = new StringMessageBuilder();
        if (message == null) {
            pushAlert("bad news", "someone (not naming names) (john) Called getMessage on an unfinished message object", "oopsies");
        } else {
            switch (message.messageID) {
                case 0x640:
                    int rpm = message.getContentVariable(0, 16);
                    RpmNumber.setText(String.format(Locale.ENGLISH, "%d", rpm));
                    break;
                case 0x641:
                    //convert from 0.01 lambda to 1 lambda
                    int lambda = message.getContentVariable(16, 16) / 100;
                    LambdaNumber.setText(String.format(Locale.ENGLISH, "%d", lambda));
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
                        //LambdaNumber.setText(String.format("%d", message.getContentVariable(40 + i, 1)));
                        if (message.getContentVariable(40 + i, 1) == 1) {
                            //warningObjects[i].warningLabel.setTextColor(Color.BLUE);
                            if (warningObjects[i] != null) {
                                warningObjects[i].timestamp = System.currentTimeMillis();
                            }
                        }
                    }
                    break;
                case 0x118:
                    //convert from 0.1 kmh to mph
                    double vehicle_speed = (message.getContentVariable(16, 8) / 10.0) * 1.609344;
                    SpeedNumber.setText(String.format(Locale.ENGLISH, "%.1f", vehicle_speed));
                    RedlineIndicator.setProgress((int) (vehicle_speed * 10));
                    RedlineIndicator.setProgressTintList(ColorStateList.valueOf(vehicle_speed > 26 || vehicle_speed < 10 ? Color.RED : Color.GREEN));
                    RedlineIndicator.setProgressTintList(ColorStateList.valueOf(vehicle_speed > 24 || vehicle_speed < 13 ? Color.YELLOW : Color.GREEN));
                    int throttlePosition = message.getContentVariable(8, 8);
                    ThrottlePositionBar.setProgress(throttlePosition);
                    int coolant_temp = message.getContentVariable(24, 8);
                    CoolantTemperature.setText(String.format(Locale.ENGLISH, "%d C", coolant_temp));
                    break;
            }

            handle_warnings();
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
            label3.setText(result);
            String[] hexBytes = result.trim().split(" ");
            label4.setText(Arrays.toString(hexBytes));
            //label1.setText(String.format(Locale.ENGLISH, "%d", hexBytes.length));
            try {
                this.messageID = this.messageID | Integer.parseInt(hexBytes[0], 16);
                for (int i = 1; i < hexBytes.length; i++) {
                    this.messageContent = this.messageContent << 8;
                    this.messageContent = this.messageContent | Integer.parseInt(hexBytes[i], 16);
                }
                label2.setText(String.format(Locale.ENGLISH, "Good message found with ID %d", this.messageID));
            } catch (Exception e) {
                label1.setText(e.toString());
            }
            if (state == MessageBuilderState.COMPLETE) {
                return new Message(this.messageType, this.messageID, this.messageContent);
            } else {
                return null;
            }
        }
    }

    /*---------- Listener class to get coordinates ------------- */
    private class MyLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location loc) {
            String longitude = "Longitude: " + loc.getLongitude();
            String latitude = "Latitude: " + loc.getLatitude();
            Log.i("SSE", String.format("Latitude: %.3f  Longitude: %.3f", loc.getLatitude(), loc.getLongitude()));
        }

        @Override
        public void onProviderDisabled(String provider) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.i("SSE", String.format("GPS status changed to: %d", status));
        }
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
                    label5.setText("Called add on complete message!");
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