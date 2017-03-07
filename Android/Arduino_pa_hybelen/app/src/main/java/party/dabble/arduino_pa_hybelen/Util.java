package party.dabble.arduino_pa_hybelen;

public class Util
{
	public static void sleep(long millis)
	{
		try
		{
			Thread.sleep(millis);
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}
}
