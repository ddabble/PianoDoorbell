/*
Code based on the answers to https://stackoverflow.com/q/2413426/5587187, mainly by Steve Pomeroy and Xarph
*/

package party.dabble.pianodoorbell;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class PlaySound
{
	// originally from http://marblemice.blogspot.com/2010/04/generate-and-play-tone-in-android.html
	// and modified by Steve Pomeroy <steve@staticfree.info>
	private static final float duration = 0.1f; // seconds
	private static final int sampleRate = 8000;
	private static int numSamples = (int)(duration * sampleRate);
	private static double sample[] = new double[numSamples];
	private static final double freqOfTone = 440; // hz

	private static final int ramp = 400 /*numSamples / 20*/; // number of samples to ramp tone up and down

	private static byte generatedSnd[] = new byte[2 * numSamples];

	public static void genTone_ramp()
	{
		// fill out the array
		for (int i = 0; i < numSamples; ++i)
		{
			// sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfTone));
			sample[i] = Math.sin((2 * Math.PI - 0.001) * i / (sampleRate / freqOfTone));
		}

		// convert to 16 bit pcm sound array
		// assumes the sample buffer is normalised.
		int idx = 0;

		for (int i = 0; i < ramp; i++)
		{
			// scale to maximum amplitude
			final short val = (short)((sample[i] * 32767) * i / ramp);
			// in 16 bit wav PCM, first byte is the low order byte
			generatedSnd[idx++] = (byte)(val & 0x00ff);
			generatedSnd[idx++] = (byte)((val & 0xff00) >>> 8);
		}

		for (int i = ramp; i < numSamples - ramp; i++)
		{
			// scale to maximum amplitude
			final short val = (short)((sample[i] * 32767));
			// in 16 bit wav PCM, first byte is the low order byte
			generatedSnd[idx++] = (byte)(val & 0x00ff);
			generatedSnd[idx++] = (byte)((val & 0xff00) >>> 8);
		}

		for (int i = numSamples - ramp; i < numSamples; i++)
		{
			// scale to maximum amplitude
			final short val = (short)((sample[i] * 32767) * (numSamples - i) / ramp);
			// in 16 bit wav PCM, first byte is the low order byte
			generatedSnd[idx++] = (byte)(val & 0x00ff);
			generatedSnd[idx++] = (byte)((val & 0xff00) >>> 8);
		}
	}

	private static volatile boolean play;
	private static Thread soundThread;

	public static void playSound_original()
	{
		final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
				sampleRate, AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
				AudioTrack.MODE_STATIC);
		audioTrack.write(generatedSnd, 0, generatedSnd.length);
		audioTrack.play();
	}

	public static void genTone(double freqOfTone)
	{
		int targetSamples = sampleRate;

		// calculate adjustments to make the sample start and stop evenly
		int numCycles = (int)(0.5 + freqOfTone * targetSamples / sampleRate);
		numSamples = (int)(0.5 + numCycles * sampleRate / freqOfTone);

		sample = new double[numSamples];

		// fill out the array
		for (int i = 0; i < numSamples; ++i)
		{
			sample[i] = Math.sin(2 * Math.PI * i / (sampleRate / freqOfTone));
		}
		// convert to 16 bit pcm sound array
		// assumes the sample buffer is normalized.

		generatedSnd = new byte[sample.length * 2];

		int idx = 0;
		for (double dVal : sample)
		{
			// scale loudness by frequency
			double amplitude = (double)(32767 * 5 / (Math.log(freqOfTone)));
			if (amplitude > 32767) amplitude = 32767;
			// scale signal to amplitude
			short val = (short)(dVal * amplitude);
			// in 16 bit wav PCM, first byte is the low order byte
			generatedSnd[idx++] = (byte)(val & 0x00ff);
			generatedSnd[idx++] = (byte)((val & 0xff00) >>> 8);
		}
	}

	private static AudioTrack audioTrack;

	public static void playSound()
	{
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
				sampleRate, AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT, numSamples * 2,
				AudioTrack.MODE_STREAM);
		audioTrack.write(generatedSnd, 0, generatedSnd.length);
		audioTrack.play();
		play = true;

		soundThread = new Thread(new Runnable()
		{
			public void run()
			{
				while (play)
				{
					audioTrack.write(generatedSnd, 0, generatedSnd.length);

//					Util.sleep((long)(duration * 1000));
				}
//				audioTrack.pause();
//				audioTrack.flush();
			}
		});

		soundThread.start();
	}

	public static void stop()
	{
		play = false;
		audioTrack.pause();
		audioTrack.flush();
	}
}
