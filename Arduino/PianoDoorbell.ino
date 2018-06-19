#include <SoftwareSerial.h>
#include <PLabBTSerial.h>

#define txPin 3
#define rxPin 2

const char BTName[] = "Anders";
const char ATCommand[] = "AT+NAMEPLab_";
PLabBTSerial btSerial(txPin, rxPin);

const int keyPins[] = { 8, 9, 10, 11, 12, 13 };

int keyLastStates[] = { HIGH, HIGH, HIGH, HIGH, HIGH, HIGH };
unsigned long keyLastStateChanges[] = { 0, 0, 0, 0, 0, 0 };

void setup()
{
  Serial.begin(9600);
  btSerial.begin(9600);
  
  Serial.print("Setting new name for device to: PLab_");
  Serial.println(BTName);

  btSerial.write(ATCommand);
  btSerial.write(BTName);
  btSerial.write(0x0D);
  btSerial.write(0x0A);
}

void loop()
{
  while (btSerial.available())
  {
    char c = btSerial.read();
    Serial.write(c);
  }

  const unsigned long currentTime = micros();
  for (int i = 0; i < sizeof(keyPins) / sizeof(int); i++)
    checkKeyState(i, currentTime);
}

void checkKeyState(const int index, const unsigned long currentTime_micros)
{
  if (currentTime_micros < keyLastStateChanges[index] + 10000)
    return;

  int keyState = digitalRead(keyPins[index]);
  if (keyState != keyLastStates[index])
  {
    int action = (keyState == HIGH) ? -1 : 1;
    btSerial.write(action * (index + 1));

    keyLastStates[index] = keyState;
    keyLastStateChanges[index] = currentTime_micros;
  }
}
