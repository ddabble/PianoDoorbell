/*
Code based on http://stackoverflow.com/a/13923345/5587187, by Majdi_la
*/

package party.dabble.pianodoorbell;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
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
	private BluetoothAdapter bluetoothAdapter;
	private BluetoothSocket bluetoothSocket;
	private BluetoothDevice bluetoothDevice;

	private OutputStream outputStream;
	private InputStream inputStream;

	private TextView myLabel;
	private EditText myTextbox;

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

		Button openButton = (Button)findViewById(R.id.open);
		Button sendButton = (Button)findViewById(R.id.send);
		Button closeButton = (Button)findViewById(R.id.close);
		myLabel = (TextView)findViewById(R.id.label);
		myTextbox = (EditText)findViewById(R.id.entry);

		//Open Button
		openButton.setOnClickListener((View v) ->
		{
			try
			{
				findBluetoothDevice();
				connectToBluetoothDevice();
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		});

		//Send Button
		sendButton.setOnClickListener((View v) ->
		{
			try
			{
				sendData();
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		});

		//Close button
		closeButton.setOnClickListener((View v) ->
		{
			try
			{
				disconnectFromBluetoothDevice();
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		});
	}

	private void findBluetoothDevice()
	{
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null)
			myLabel.setText("No bluetooth adapter available");

		if (!bluetoothAdapter.isEnabled())
		{
			Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBluetooth, 0);
		}

		Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
		if (pairedDevices.size() > 0)
		{
			for (BluetoothDevice device : pairedDevices)
			{
				String name = device.getName().trim();
				if (name.equals("PLab_Anders"))
				{
					bluetoothDevice = device;
					break;
				}
			}
		}
		myLabel.setText("Bluetooth Device Found");
	}

	private void connectToBluetoothDevice() throws IOException
	{
		UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
		bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
		bluetoothSocket.connect();
		outputStream = bluetoothSocket.getOutputStream();
		inputStream = bluetoothSocket.getInputStream();

		beginListeningForData();

		myLabel.setText("Bluetooth Opened");
	}

	private void beginListeningForData()
	{
		keepListening = true;
		listenerThread = new Thread(() ->
		{
			key1 = new PianoKey(440.00f);
			key2 = new PianoKey(466.16f);
			key3 = new PianoKey(493.88f);
			key4 = new PianoKey(523.25f);
			key5 = new PianoKey(554.37f);
			key6 = new PianoKey(587.33f);

//			key7 = new PianoKey(622.25f);
//			key8 = new PianoKey(659.25f);
//			key9 = new PianoKey(698.46f);
//			key10 = new PianoKey(739.99f);
//			key11 = new PianoKey(783.99f);
//			key12 = new PianoKey(830.61f);

			while (keepListening && !Thread.currentThread().isInterrupted())
			{
				try
				{
					int bytesAvailable = inputStream.available();
					if (bytesAvailable <= 0)
						continue;

					byte[] packetBytes = new byte[bytesAvailable];
					inputStream.read(packetBytes);
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
				} catch (IOException ex)
				{
					keepListening = false;
				}
			}
			terminatePianoKeys();
		});

		listenerThread.start();
	}

	private void sendData() throws IOException
	{
		String msg = myTextbox.getText().toString();
		msg += "\n";
		outputStream.write(msg.getBytes());
		myLabel.setText("Data Sent");
	}

	private void disconnectFromBluetoothDevice() throws IOException
	{
		keepListening = false;
		terminatePianoKeys();
		outputStream.close();
		inputStream.close();
		bluetoothSocket.close();
		myLabel.setText("Bluetooth Closed");
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
}
