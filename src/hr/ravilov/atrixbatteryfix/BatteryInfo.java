package hr.ravilov.atrixbatteryfix;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Scanner;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public class BatteryInfo {
	final private static long MAX_TIME = 30 * 60 * 1000;	// in ms - 30 minutes

	static private final String dir = "/sys/class/power_supply/battery";
	static public boolean isOnAC = false;
	static public boolean isOnUSB = false;
	static public boolean isOnPower = false;
	static public boolean isCharging = false;
	static public boolean isDischarging = false;
	static public boolean isFull = false;
	static public boolean seemsFull = false;
	static public String battActual;
	static public String battShown;
	static public String battHealth;
	static public String battVoltage;
	static public String battTemp;
	static private int state;
	static private int plugged;
	static private String lastVoltage = null;
	static private long lastTime = -1;

	static public void init() {
		init(null);
	}

	static public void init(Intent i) {
		if (i == null) {
			refresh();
		} else {
			refresh(i);
		}
	}

	static private String getFile(String filename) {
		try {
			Scanner sc = new Scanner(new FileInputStream(dir + "/" + filename), "UTF-8");
			if (!sc.hasNextLine()) {
				return null;
			}
			String ret = sc.nextLine();
			ret = ret.replaceAll("(^[\\s\\r\\n]+|[\\s\\r\\n]+$)", "");
			return ret;
		}
		catch (FileNotFoundException ex) { }
		return null;
	}

	static public void refresh(Intent i) {
		battHealth = getFile("health");
		battActual = getFile("capacity");
		battShown = getFile("charge_counter");
		battVoltage = getFile("voltage_now");
		battTemp = getFile("temp");
		if (i == null) {
			i = MyUtils.getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		}
		plugged = i.getIntExtra("plugged", -1);
		isOnAC = (plugged == BatteryManager.BATTERY_PLUGGED_AC) ? true : false;
		isOnUSB = (plugged == BatteryManager.BATTERY_PLUGGED_USB) ? true : false;
		isOnPower = (isOnAC || isOnUSB) ? true : false;
		state = i.getIntExtra("status", -1);
		isCharging = (state == BatteryManager.BATTERY_STATUS_CHARGING) ? true : false;
		isDischarging = (state == BatteryManager.BATTERY_STATUS_DISCHARGING) ? true : false;
		isFull = (state == BatteryManager.BATTERY_STATUS_FULL) ? true : false;
		if (!isFull) {
			// workaround for when the battery is not at 100% but is done charging anyway
			isFull = (isOnPower && !isCharging && !isDischarging) ? true : false;
			if (!isFull) {
				if (battActual != null && battActual.equals("100")) {
					isFull = true;
				}
			}
		}
		// heuristics for detecting if battery is done charging
		// if the voltage does not change within MAX_TIME milliseconds, the battery is considered charged
		seemsFull = isFull ? true : false;
		if (isOnPower && !isFull && battVoltage != null) {
			if (lastVoltage == null || lastTime <= 0 || !lastVoltage.equals(battVoltage)) {
				lastVoltage = battVoltage;
				lastTime = System.currentTimeMillis();
			} else {
				seemsFull = (System.currentTimeMillis() - lastTime >= MAX_TIME) ? true : false;
			}
		} else {
			lastVoltage = null;
			lastTime = -1;
			return;
		}
		log();
	}

	static public void refresh() {
		refresh(null);
	}

	static String getValue(String v) {
		if (v == null) {
			return "-";
		}
		return v;
	}

	static public void log(String tag) {
		MyUtils.log(tag, String.format("health=[%s] voltage=[%s] temp=[%s] actual=[%s] shown=[%s] -- plugged=[%d] state=[%d] -- onAc=[%s] onUsb=[%s] isCharging=[%s] isDischarging=[%s] isFull=[%s] seemsFull=[%s] -- lastVoltage=[%s] lastTime=[%d]",
			getValue(battHealth),
			getValue(battVoltage),
			getValue(battTemp),
			getValue(battActual),
			getValue(battShown),
			plugged,
			state,
			isOnAC ? "YES" : "NO",
			isOnUSB ? "YES" : "NO",
			isCharging ? "YES" : "NO",
			isDischarging ? "YES" : "NO",
			isFull ? "YES" : "NO",
			seemsFull ? "YES" : "NO",
			lastVoltage,
			lastTime
		));
	}

	static public void log() {
		log("<battery>");
	}
}
