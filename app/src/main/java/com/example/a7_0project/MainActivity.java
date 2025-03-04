package com.example.a7_0project;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String ACTION_USB_PERMISSION = "com.android.example.a7_0project.USB_PERMISSION";
    private static final String ACTION_USB_DEVICE_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    private static final String ACTION_USB_DEVICE_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";

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

    private BroadcastReceiver broadcastReceiver = null;
    private SerialInputOutputManager usbIoManager;

    public static boolean threadLock = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.setContentView(R.layout.layout);

        this.label1 = this.findViewById(R.id.label1);
        this.label2 = this.findViewById(R.id.label2);
        this.label3 = this.findViewById(R.id.label3);
        this.label4 = this.findViewById(R.id.label4);
        this.label5 = this.findViewById(R.id.label5);
        this.RpmNumber = this.findViewById(R.id.rpmNumber);
        this.SpeedNumber = this.findViewById(R.id.speedNumber);
        this.RedlineIndicator = this.findViewById(R.id.redlineIndicator);

        currentMessageBuilder = new StringMessageBuilder();


        // Find all available USB drivers (for the attached devices?).
        this.usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        //create an object that receives all system events, and filters for USBs being plugged in
        //then connects to them
        this.broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    pushAlert("New broadcast received", intent.getAction(), "neat");
                    label1.setText(intent.getAction());
                    //done this way because ACTION_USB is guaranteed to not be a null
                    if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                        //connect();
                    } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                        usbIoManager.stop();
                    }
                    else if (UsbManager.EXTRA_PERMISSION_GRANTED.equals(intent.getAction())) {
                        connect();
                    }
                }
                catch (Exception e) {
                    pushAlert("onReceive ERORR", Arrays.toString(e.getStackTrace()), "darn");
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        //filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        //filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.EXTRA_PERMISSION_GRANTED);
        //filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        //filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(broadcastReceiver, filter);

        //ContextCompat.registerReceiver(getBaseContext(), broadcastReceiver, new IntentFilter(ACTION_USB_DEVICE_ATTACHED), ContextCompat.RECEIVER_NOT_EXPORTED);

        this.serialButton = this.findViewById(R.id.serial_button);
        //pushAlert("game laujnched", "da game was launched", "gaming");
        this.serialButton.setOnClickListener(v -> {
            connect();
        });
    }

    private void connect() {
        try {
            //list all devices in label 1
            label1.setText(usbManager.getDeviceList().toString());

            //find all usb drivers
            List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
            if (availableDrivers.isEmpty()) {
                serialButton.setBackgroundColor(Color.MAGENTA);
                pushAlert("MAJOR ERROR:", "No available USB drivers? This really should not happen...", "darn");
                return;
            }
            //print the first found usb driver to label 2
            label2.setText(availableDrivers.get(0).toString());

//            PendingIntent permissionIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
//            UsbDevice device = usbManager.getDeviceList().get(usbManager.getDeviceList().keySet().iterator().next());
//            usbManager.requestPermission(device, permissionIntent);

            label3.setText(String.valueOf(usbManager.hasPermission(usbManager.getDeviceList().get(usbManager.getDeviceList().keySet().iterator().next()))));

            // Open a connection to the first available driver.
            UsbSerialDriver driver = availableDrivers.get(0);
            UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());
            if (connection == null) {
                serialButton.setBackgroundColor(Color.RED);
                return;
            }

            label4.setText(String.valueOf(connection));

            UsbSerialPort port = driver.getPorts().get(0); // Most devices have just one port (port 0)

            port.open(connection);
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            //port.write("hello".getBytes(), 1000);
            SerialHandler serialHandler = new SerialHandler();
            this.usbIoManager = new SerialInputOutputManager(port, serialHandler);
            usbIoManager.start();
        }
        catch (Exception e) {
            pushAlert("ERROR (button)", e.getMessage(), "darn");
        }
    }

    public class SerialHandler extends Fragment implements SerialInputOutputManager.Listener {
        @Override
        public void onNewData(byte[] data) {
            runOnUiThread(() -> {
                if (threadLock) {
                    pushAlert("ERROR", "Threadlock violated", "darn");
                }
                threadLock = true;
                try {
                    StringBuilder stringBuilder = new StringBuilder();
                    for (byte b : data) {
                        stringBuilder.append((char) b);
                        MessageBuilderState msgb_state = currentMessageBuilder.add(b);
                        if (msgb_state == MessageBuilderState.COMPLETE) {
                            serialButton.setBackgroundColor(Color.GREEN);
                            Message message = currentMessageBuilder.getMessage();
                            currentMessageBuilder = new StringMessageBuilder();
                            if (message == null) {
                                pushAlert("bad news", "someone (not naming names) (john) Called getMessage on an unfinished message object", "oopsies");
                            }
                            else {
                                RpmNumber.setText(String.format(Locale.ENGLISH, "%d", message.messageContent & 0b11111111));
                                RedlineIndicator.setProgress((int) message.messageContent);
                                RedlineIndicator.setProgressTintList(ColorStateList.valueOf(message.messageContent > 220 ? Color.RED : Color.rgb(4, 142, 4)));
                                //label2.setText(String.format(Locale.ENGLISH, "Message ID: %d", message.messageID));
                            }
                        }
                    }
                    //serialButton.setBackgroundColor(Color.YELLOW);
                    StringBuilder newStringBuilber = new StringBuilder();
                    for (byte b : stringBuilder.toString().getBytes()) {
                        newStringBuilber.append(String.format("|%c", (char) b));
                    }
                    label5.setText(newStringBuilber.toString());
                    threadLock = false;
                }
                catch (Exception e) {
                    pushAlert("ERROR (newData)", e.getMessage() + Arrays.toString(e.getStackTrace()), "darn");
                }
            });
        }

        @Override
        public void onRunError(Exception e) {
            usbIoManager.stop();
            pushAlert("On Run Error Called", Arrays.toString(e.getStackTrace()), "yikes");
            label5.setText(e.getMessage());
        }
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
    }

    enum MessageBuilderState{WAITING, MESSAGE_TYPE, MESSAGE_ID, MESSAGE_CONTENT, COMPLETE}

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
            label1.setText( String.format(Locale.ENGLISH ,"%d", hexBytes.length));
            try {
                this.messageID = this.messageID | Integer.parseInt(hexBytes[0], 16);
                for (int i = 1; i < hexBytes.length; i++) {
                    this.messageContent = this.messageContent << 8;
                    this.messageContent = this.messageContent | Integer.parseInt(hexBytes[i], 16);
                }
                label2.setText(String.format(Locale.ENGLISH, "Good message found with ID %d", this.messageID));
            }
            catch (Exception e) {
                label1.setText(e.toString());
            }
            if (state == MessageBuilderState.COMPLETE) {
                return new Message(this.messageType, this.messageID, this.messageContent);
            }
            else {
                return null;
            }
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
                    }
                    else if ((char) b == ']') {
                        this.state = MessageBuilderState.MESSAGE_ID;
                        this.remainingBytes = 4;
                    }
                    else {
                        this.state = MessageBuilderState.WAITING;
                    }
                    break;
                case MESSAGE_ID:
                    if (remainingBytes > 0) {
                        this.messageID = (this.messageID & b) << 8;
                        remainingBytes--;
                    }
                    else {
                        this.state = MessageBuilderState.MESSAGE_CONTENT;
                        remainingBytes = 8;
                    }
                    break;
                case MESSAGE_CONTENT:
                    if (remainingBytes > 0) {
                        this.messageContent = (this.messageContent & b) << 8;
                        remainingBytes--;
                    }
                    else if ((char) b == '\n') {
                        this.state = MessageBuilderState.COMPLETE;
                    }
                    else {
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
            }
            else {
                return null;
            }
        }
    }
}