/*
Code based on http://blog.workingsi.com/2012/03/android-tone-generator-app.html, by siliconfish,
	which is based on the answers to https://stackoverflow.com/q/2413426/5587187, mainly by Steve Pomeroy and Xarph,
		which is based on https://marblemice.blogspot.com/2010/04/generate-and-play-tone-in-android.html, by Paul Reeves
*/

package party.dabble.pianodoorbell;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class PianoKey
{
	private static final int SAMPLE_RATE = 8000;
	private static final float DURATION = 0.1f; // seconds

	private final float toneFrequency;
	private byte[] generatedSound;

	private final AudioTrack audioTrack;

	private Thread soundThread;
	private volatile boolean terminate = false;
	private volatile boolean play;

	public PianoKey(float toneFrequency)
	{
		this.toneFrequency = toneFrequency;

		generateSoundData();

		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
				SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT, generatedSound.length,
				AudioTrack.MODE_STREAM);

		startSoundThread();
	}

	private void generateSoundData()
	{
		// calculate adjustments to make the sample start and stop evenly
		int numCycles = (int)(0.5 + toneFrequency);
		int numSamples = (int)(0.5 + numCycles * SAMPLE_RATE / toneFrequency);

		double[] samples = new double[numSamples];

		// fill out the array
		for (int i = 0; i < numSamples; i++)
			samples[i] = Math.sin(2 * Math.PI * i / (SAMPLE_RATE / toneFrequency));

		// convert to 16 bit pcm sound array
		// assumes the sample buffer is normalized.
		generatedSound = new byte[samples.length * 2];
		int idx = 0;
		for (double sample : samples)
		{
			// scale loudness by frequency
			double amplitude = 0x7FFF * 5 / Math.log(toneFrequency);
			if (amplitude > 0x7FFF)
				amplitude = 0x7FFF;
			// scale signal to amplitude
			short val = (short)(sample * amplitude);
			// in 16 bit wav PCM, first byte is the low order byte
			generatedSound[idx++] = (byte)(val & 0x00FF);
			generatedSound[idx++] = (byte)((val & 0xFF00) >>> 8);
		}
	}

	private void startSoundThread()
	{
		soundThread = new Thread(() ->
		{
			while (!terminate)
			{
				while (play)
				{
					audioTrack.write(generatedSound, 0, generatedSound.length);

					Util.sleep((long)(DURATION * 1000));
				}

				Util.sleep(10);
			}
		});
		soundThread.start();
	}

	public void terminate()
	{
		terminate = true;
		stop();
		audioTrack.release();

		try
		{
			soundThread.join();
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	public void play()
	{
		if (play)
			return;

		synchronized (audioTrack)
		{
			audioTrack.write(generatedSound, 0, generatedSound.length);
			audioTrack.play();
		}

		play = true;
	}

	public void stop()
	{
		if (!play)
			return;

		synchronized (audioTrack)
		{
			play = false;

			audioTrack.pause();
			audioTrack.flush();
		}
	}
}
