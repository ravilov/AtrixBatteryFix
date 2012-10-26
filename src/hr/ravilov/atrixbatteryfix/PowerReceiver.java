package hr.ravilov.atrixbatteryfix;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import hr.ravilov.atrixbatteryfix.BatteryFix;

public class PowerReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(final Context ctx, final Intent i) {
		Thread th;
		th = new Thread(new Runnable() {
			public void run() {
				try {
					Thread.sleep(350);
				}
				catch (Exception ex) { }
				MyUtils.init(ctx);
				Settings.init();
				BatteryInfo.init();
				BatteryFix.init(true);
				BatteryFix.checkPower(true);
			}
		});
		th.setDaemon(true);
		th.start();
	}
}
