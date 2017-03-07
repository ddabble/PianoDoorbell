/*
Code based on http://blog.workingsi.com/2012/03/android-tone-generator-app.html, by siliconfish
*/

package party.dabble.arduino_pa_hybelen;

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
	private static final int RAMP = 400 /*numSamples / 20*/; // number of samples to ramp tone up and down

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
//					audioTrack.pause();
//					audioTrack.flush();

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
		{
			samples[i] = Math.sin(2 * Math.PI * i / (SAMPLE_RATE / toneFrequency));
		}
		// convert to 16 bit pcm sound array
		// assumes the sample buffer is normalized.

		generatedSnd = new byte[samples.length * 2];

		int idx = 0;
//		final int rampStartIndex = Math.max(0, samples.length - RAMP);
		for (int i = 0; i < samples.length /*rampStartIndex*/; i++)
		{
			// scale loudness by frequency
			double amplitude = (double)(32767 * 5 / (Math.log(toneFrequency)));
			if (amplitude > 32767) amplitude = 32767;
			// scale signal to amplitude
			short val = (short)(samples[i] * amplitude);
			// in 16 bit wav PCM, first byte is the low order byte
			generatedSnd[idx++] = (byte)(val & 0x00ff);
			generatedSnd[idx++] = (byte)((val & 0xff00) >>> 8);
		}

//		for (int i = rampStartIndex; i < samples.length; i++)
//		{
//			// scale loudness by frequency
//			double amplitude = (double)(32767 * 5 / (Math.log(toneFrequency)));
//			if (amplitude > 32767) amplitude = 32767;
//			// scale signal to amplitude
//			short val = (short)((samples[i] * amplitude) * (samples.length - i) / samples.length);
//			// in 16 bit wav PCM, first byte is the low order byte
//			generatedSnd[idx++] = (byte)(val & 0x00ff);
//			generatedSnd[idx++] = (byte)((val & 0xff00) >>> 8);
//		}
	}

	public void play()
	{
		if (play)
			return;

//		if (soundThread != null)
//		{
//			try
//			{
//				soundThread.join();
//			} catch (InterruptedException e)
//			{
//				e.printStackTrace();
//			}
//		}

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

//			audioTrack.stop();
		}
	}

	public static byte[] genRampDownTone(float toneFrequency)
	{
		final int targetSamples = RAMP;

		// calculate adjustments to make the sample start and stop evenly
		int numCycles = (int)(0.5 + toneFrequency * targetSamples / SAMPLE_RATE);
		numSamples = (int)(0.5 + numCycles * SAMPLE_RATE / toneFrequency);

		double[] samples = new double[numSamples];

		// fill out the array
		for (int i = 0; i < numSamples; ++i)
		{
			samples[i] = Math.sin(2 * Math.PI * i / (SAMPLE_RATE / toneFrequency));
		}
		// convert to 16 bit pcm sound array
		// assumes the sample buffer is normalized.

		byte[] generatedSnd = new byte[samples.length * 2];

		int idx = 0;
		for (int i = 0; i < samples.length; i++)
		{
			// scale loudness by frequency
			double amplitude = (double)(32767 * 5 / (Math.log(toneFrequency)));
			if (amplitude > 32767) amplitude = 32767;
			// scale signal to amplitude
			short val = (short)((samples[i] * amplitude) * (samples.length - i) / samples.length);
			// in 16 bit wav PCM, first byte is the low order byte
			generatedSnd[idx++] = (byte)(val & 0x00ff);
			generatedSnd[idx++] = (byte)((val & 0xff00) >>> 8);
		}

		return generatedSnd;
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

/*
	public void play()
	{
		if (play)
			return;

		soundThread = new Thread(new Runnable()
		{
			public void run()
			{
				int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
				if (minBuffer <= 0)
					return;

				track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
						AudioFormat.ENCODING_PCM_16BIT, minBuffer, AudioTrack.MODE_STREAM);
				track.play();

//				byte[] sound = genStartSound(toneFrequency);
//				track.write(sound, 0, sound.length);

				play = true;
				while (play)
				{
//					sound = genMiddleSound(toneFrequency);
//					track.write(sound, 0, sound.length);
					byte[] sound = genSound(toneFrequency);
					track.write(sound, 0, sound.length);
				}
			}
		});

		soundThread.start();
	}

	private static byte[] genStartSound(float frequency)
	{
		double[] samples = genSamples(RAMP, frequency, 0);

		// convert to 16 bit pcm sound array
		// assumes the sample buffer is normalised.
		int idx = 0;
		byte generatedSnd[] = new byte[2 * samples.length];

		for (int i = 0; i < samples.length; i++)
		{
			// scale to maximum amplitude
			final short val = (short)((samples[i] * 32767) * i / RAMP);
			// in 16 bit wav PCM, first byte is the low order byte
			generatedSnd[idx++] = (byte)(val & 0x00ff);
			generatedSnd[idx++] = (byte)((val & 0xff00) >>> 8);
		}

		return generatedSnd;
	}

	private static byte[] genMiddleSound(float frequency)
	{
		float tonePeriod = SAMPLE_RATE / frequency;
		int numPeriods = (int)(1024 / tonePeriod) + 1; // number of tone periods to cover 1024 samples
		double[] samples = genSamples((int)(numPeriods * tonePeriod), frequency, RAMP);

		// convert to 16 bit pcm sound array
		// assumes the sample buffer is normalised.
		int idx = 0;
		byte generatedSnd[] = new byte[2 * samples.length];

		for (int i = 0; i < samples.length; i++)
		{
			// scale to maximum amplitude
			final short val = (short)(samples[i] * 32767);
			// in 16 bit wav PCM, first byte is the low order byte
			generatedSnd[idx++] = (byte)(val & 0x00ff);
			generatedSnd[idx++] = (byte)((val & 0xff00) >>> 8);
		}

		return generatedSnd;
	}

	private static byte[] genEndSound(float frequency)
	{
		double[] samples = genSamples(RAMP, frequency, RAMP);

		// convert to 16 bit pcm sound array
		// assumes the sample buffer is normalised.
		int idx = 0;
		byte generatedSnd[] = new byte[2 * samples.length];

		for (int i = 0; i < samples.length; i++)
		{
			// scale to maximum amplitude
			final short val = (short)((samples[i] * 32767) * (samples.length - i) / RAMP);
			// in 16 bit wav PCM, first byte is the low order byte
			generatedSnd[idx++] = (byte)(val & 0x00ff);
			generatedSnd[idx++] = (byte)((val & 0xff00) >>> 8);
		}

		return generatedSnd;
	}

	private static double[] genSamples(int numSamples, float toneFrequency, int offset)
	{
		double[] samples = new double[numSamples];

		for (int i = 0; i < numSamples; i++)
		{
			// sample[i] = Math.sin(2 * Math.PI * i / (SAMPLE_RATE / freqOfTone));
			samples[i] = Math.sin((2 * Math.PI - 0.001) * (i + offset) / (SAMPLE_RATE / toneFrequency));
		}

		return samples;
	}
*/
}
