/*
Code based on http://stackoverflow.com/a/13923345/5587187, by Majdi_la
*/

package party.dabble.pianodoorbell;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity
{
	private static final String DEFAULT_BLUETOOTH_DEVICE_NAME = "PLab_Anders";
	private static final long DEFAULT_NOTIFICATION_DELAY = 200; // milliseconds

	private BluetoothAdapter bluetoothAdapter;
	private BluetoothSocket bluetoothSocket;
	private BluetoothDevice bluetoothDevice;

	private OutputStream outputStream;
	private InputStream inputStream;

	private EditText deviceNameBox;
	private EditText messageBox;
	private TextView notificationText;

	private Button connectButton;
	private Button disconnectButton;
	private Button sendButton;

	private Thread listenerThread;
	private volatile boolean keepListening;

	private PianoKey key1;
	private PianoKey key2;
	private PianoKey key3;
	private PianoKey key4;
	private PianoKey key5;
	private PianoKey key6;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		deviceNameBox = findViewById(R.id.device);
		deviceNameBox.setText(DEFAULT_BLUETOOTH_DEVICE_NAME);

		messageBox = findViewById(R.id.message);

		connectButton = findViewById(R.id.connect);
		disconnectButton = findViewById(R.id.disconnect);
		sendButton = findViewById(R.id.send);

		notificationText = findViewById(R.id.notification);

		// Connect button
		connectButton.setOnClickListener((View v) ->
		{
			final String deviceName = deviceNameBox.getText().toString();
			bluetoothDevice = findBluetoothDevice(deviceName);
			if (bluetoothDevice == null)
				return;

			try
			{
				connectToBluetoothDevice();
			} catch (IOException e)
			{
				showError("Could not connect to Bluetooth device \"" + deviceName + "\"", DEFAULT_NOTIFICATION_DELAY);
				e.printStackTrace();
			}
		});

		// Disconnect button
		disconnectButton.setOnClickListener((View v) ->
		{
			disconnectFromBluetoothDevice();
		});

		// Send button
		sendButton.setOnClickListener((View v) ->
		{
			sendData(messageBox.getText().toString());
		});
	}

	private BluetoothDevice findBluetoothDevice(String deviceName)
	{
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null)
		{
			showError("No Bluetooth adapter available", DEFAULT_NOTIFICATION_DELAY);
			return null;
		}

		if (!bluetoothAdapter.isEnabled())
		{
			Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBluetooth, 0);
			showError("Bluetooth turned off; please try again", DEFAULT_NOTIFICATION_DELAY);
			return null;
		}

		Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
		if (pairedDevices.size() > 0)
		{
			for (BluetoothDevice device : pairedDevices)
			{
				String name = device.getName().trim();
				if (name.equals(deviceName))
					return device;
			}
		}
		showError("Paired Bluetooth device \"" + deviceName + "\" not found", DEFAULT_NOTIFICATION_DELAY);
		return null;
	}

	private void connectToBluetoothDevice() throws IOException
	{
		showText("", 0); // Clear text first, since connecting usually takes pretty long

		UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
		bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
		bluetoothSocket.connect();
		outputStream = bluetoothSocket.getOutputStream();
		inputStream = bluetoothSocket.getInputStream();

		beginListeningForData();

		showText("Connected to Bluetooth device", DEFAULT_NOTIFICATION_DELAY);
	}

	private void disconnectFromBluetoothDevice()
	{
		keepListening = false;
		terminatePianoKeys();

		try
		{
			outputStream.close();
			inputStream.close();
			bluetoothSocket.close();
		} catch (IOException e)
		{
			showError("Error while closing Bluetooth connection", DEFAULT_NOTIFICATION_DELAY);
			e.printStackTrace();
			return;
		}

		showText("Disconnected from Bluetooth device", DEFAULT_NOTIFICATION_DELAY);
	}

	private void sendData(String message)
	{
		message += "\n";
		try
		{
			outputStream.write(message.getBytes());
		} catch (IOException e)
		{
			showError("Error while sending data", DEFAULT_NOTIFICATION_DELAY);
			e.printStackTrace();
			return;
		}

		showText("Data sent", DEFAULT_NOTIFICATION_DELAY);
	}

	private void beginListeningForData()
	{
		keepListening = true;
		listenerThread = new Thread(() ->
		{
			initPianoKeys();

			while (keepListening && !Thread.currentThread().isInterrupted())
			{
				int bytesAvailable;
				byte[] packetBytes;
				try
				{
					bytesAvailable = inputStream.available();
					if (bytesAvailable <= 0)
						continue;

					packetBytes = new byte[bytesAvailable];
					inputStream.read(packetBytes);
				} catch (IOException ex)
				{
					keepListening = false;
					showError("Error while listening to Bluetooth device; please try connecting again", DEFAULT_NOTIFICATION_DELAY);
					break;
				}

				for (int i = 0; i < bytesAvailable; i++)
				{
					final byte b = packetBytes[i];

					byte key = (byte)Math.abs(b);
					byte action = (byte)(b >>> 7);

					PianoKey pianoKey;
					switch (key)
					{
						case 1:
							pianoKey = key1;
							break;
						case 2:
							pianoKey = key2;
							break;
						case 3:
							pianoKey = key3;
							break;
						case 4:
							pianoKey = key4;
							break;
						case 5:
							pianoKey = key5;
							break;
						case 6:
							pianoKey = key6;
							break;
						default:
							continue;
					}

					if (action == 0)
						pianoKey.play();
					else
						pianoKey.stop();
				}
			}

			terminatePianoKeys();
		});
		listenerThread.start();
	}

	private void initPianoKeys()
	{
		key1 = new PianoKey(440.00f);
		key2 = new PianoKey(466.16f);
		key3 = new PianoKey(493.88f);
		key4 = new PianoKey(523.25f);
		key5 = new PianoKey(554.37f);
		key6 = new PianoKey(587.33f);

//		key7 = new PianoKey(622.25f);
//		key8 = new PianoKey(659.25f);
//		key9 = new PianoKey(698.46f);
//		key10 = new PianoKey(739.99f);
//		key11 = new PianoKey(783.99f);
//		key12 = new PianoKey(830.61f);
	}

	private void terminatePianoKeys()
	{
		key1.terminate();
		key2.terminate();
		key3.terminate();
		key4.terminate();
		key5.terminate();
		key6.terminate();
	}

	private void showText(String notification, long delayMillis)
	{
		showText(notification, "#8A000000", delayMillis); // default text color
	}

	private void showError(String notification, long delayMillis)
	{
		showText(notification, "#FF4081", delayMillis); // @color/colorAccent
	}

	private void showText(final String notification, final String colorHex, final long delayMillis)
	{
		runOnUiThread(() ->
		{
			if (delayMillis > 0)
				showText("", 0); // Clear text, to give the user feedback even when the text doesn't change

			new Handler().postDelayed(() ->
			{
				notificationText.setTextColor(Color.parseColor(colorHex));
				notificationText.setText(notification);
			}, delayMillis);
		});
	}
}
