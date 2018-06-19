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
	private final float toneFrequency;

	private Thread soundThread;
	private volatile boolean terminate = false;
	private volatile boolean play;
	private final AudioTrack audioTrack;

	private static final int SAMPLE_RATE = 8000;
	private static final float duration = 0.1f; // seconds
	private static int numSamples = (int)(duration * SAMPLE_RATE);

	private byte[] generatedSnd = new byte[2 * numSamples];

	public PianoKey(float toneFrequency)
	{
		this.toneFrequency = toneFrequency;

		genTone();

		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
				SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
				AudioTrack.MODE_STREAM);

		soundThread = new Thread(new Runnable()
		{
			public void run()
			{
				audioTrack.write(generatedSnd, 0, generatedSnd.length);
				audioTrack.play();
				play = true;

				while (!terminate)
				{
					while (play)
					{
						audioTrack.write(generatedSnd, 0, generatedSnd.length);

						Util.sleep((long)(duration * 1000));
					}

					Util.sleep(10);
				}
			}
		});
	}

	public void genTone()
	{
		final int targetSamples = SAMPLE_RATE;

		// calculate adjustments to make the sample start and stop evenly
		int numCycles = (int)(0.5 + toneFrequency * targetSamples / SAMPLE_RATE);
		numSamples = (int)(0.5 + numCycles * SAMPLE_RATE / toneFrequency);

		double[] samples = new double[numSamples];

		// fill out the array
		for (int i = 0; i < numSamples; ++i)
			samples[i] = Math.sin(2 * Math.PI * i / (SAMPLE_RATE / toneFrequency));
		// convert to 16 bit pcm sound array
		// assumes the sample buffer is normalized.

		generatedSnd = new byte[samples.length * 2];

		int idx = 0;
		for (int i = 0; i < samples.length; i++)
		{
			// scale loudness by frequency
			double amplitude = (double)(32767 * 5 / (Math.log(toneFrequency)));
			if (amplitude > 32767)
				amplitude = 32767;
			// scale signal to amplitude
			short val = (short)(samples[i] * amplitude);
			// in 16 bit wav PCM, first byte is the low order byte
			generatedSnd[idx++] = (byte)(val & 0x00ff);
			generatedSnd[idx++] = (byte)((val & 0xff00) >>> 8);
		}
	}

	public void play()
	{
		if (play)
			return;

		if (!soundThread.isAlive())
		{
			soundThread.start();
			return;
		}

		synchronized (audioTrack)
		{
			audioTrack.write(generatedSnd, 0, generatedSnd.length);
			audioTrack.play();
		}

		play = true;
	}

	public void stop()
	{
		synchronized (audioTrack)
		{
			play = false;

			audioTrack.pause();
			audioTrack.flush();
		}
	}

	public void terminate()
	{
		terminate = true;
		stop();

		try
		{
			soundThread.join();
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}
}
