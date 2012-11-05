package hr.ravilov.atrixbatteryfix;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;

public class PowerReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(final Context ctx, final Intent i) {
		Thread th = new Thread(new Runnable() {
			public void run() {
				try {
					//Thread.sleep(350);
					long time = System.currentTimeMillis();
					while (System.currentTimeMillis() < time + 350) {
						Thread.yield();
					}
				}
				catch (Exception ex) { }
				Looper.prepare();
				MyUtils utils = new MyUtils(ctx);
				BatteryFix fix = new BatteryFix(utils, (new Settings()).init(utils), new BatteryInfo(utils), true);
				fix.checkPower();
				Looper.loop();
				Looper.myLooper().quit();
			}
		});
		th.setDaemon(true);
		th.start();
	}
}
