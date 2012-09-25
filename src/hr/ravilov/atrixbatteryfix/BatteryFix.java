package hr.ravilov.atrixbatteryfix;

import java.util.zip.CRC32;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.widget.Toast;
import hr.ravilov.atrixbatteryfix.MyUtils;
import hr.ravilov.atrixbatteryfix.MonitorService;
import hr.ravilov.atrixbatteryfix.R;

public class BatteryFix {
	private static Context ctx;
	private static boolean triggered;
	private static SharedPreferences prefs;
	public static boolean autoFix;
	public static boolean showNotifications;
	public static boolean autoReboot;
	private static NotificationManager nm;

	static public void init(Context c, boolean trig) {
		ctx = c;
		triggered = trig;
		nm = (NotificationManager)getContext().getSystemService(Context.NOTIFICATION_SERVICE);
		prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		loadPrefs();
	}

	static public void loadPrefs() {
		autoFix = prefs.getBoolean(MyUtils.PREF_AUTOFIX, true);
		showNotifications = prefs.getBoolean(MyUtils.PREF_NOTIFICATIONS, true);
		autoReboot = prefs.getBoolean(MyUtils.PREF_AUTOREBOOT, false);
	}

	static public void savePrefs() {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(MyUtils.PREF_AUTOFIX, autoFix);
		editor.putBoolean(MyUtils.PREF_NOTIFICATIONS, showNotifications);
		editor.putBoolean(MyUtils.PREF_AUTOREBOOT, autoReboot);
		editor.commit();
	}

	static public boolean run() {
		BatteryInfo.refresh();
		try {
			if (showNotifications) {
				showNotification(ctx.getText(triggered ? R.string.msg_autocalibrate : R.string.msg_calibrate).toString());
			}
			recalibrate();
			if (triggered && showNotifications) {
				Toast.makeText(ctx, ctx.getText(autoReboot ? R.string.msg_done : R.string.msg_done_reminder).toString(), Toast.LENGTH_LONG).show();
			}
		}
		catch (Exception ex) {
			Toast.makeText(ctx, String.format(ctx.getText(R.string.err_calibrate).toString(), ex.getMessage()), Toast.LENGTH_LONG).show();
			return false;
		}
		return true;
	}

	static public boolean fix() {
		BatteryInfo.refresh();
		try {
			if (showNotifications) {
				showNotification(ctx.getText(R.string.msg_fixing).toString());
			}
			fixBattd();
			if (showNotifications) {
				Toast.makeText(ctx, ctx.getText(R.string.msg_fixed).toString(), Toast.LENGTH_SHORT).show();
			}
		}
		catch (Exception ex) {
			Toast.makeText(ctx, String.format(ctx.getText(R.string.err_fix).toString(), ex.getMessage()), Toast.LENGTH_LONG).show();
			return false;
		}
		return true;
	}

	static public void showNotification(String str) {
		CRC32 gen = new CRC32();
		gen.update(str.getBytes());
		int id = (int)gen.getValue();
		nm.cancel(id);
		Notification ntf = new Notification(R.drawable.icon, str, System.currentTimeMillis());
		ntf.flags |= Notification.FLAG_AUTO_CANCEL;
		try {
			ntf.setLatestEventInfo(ctx, null, null, PendingIntent.getActivity(ctx, 0, new Intent(), 0));
		}
		catch (Exception ex) { }
		nm.notify(id, ntf);
		nm.cancel(id);
	}

	static public void recalibrate() throws Exception {
		if (MyUtils.findBusybox() == null) {
			throw new Exception(getContext().getText(R.string.err_busybox).toString());
		}
		// test run, to see if we can su at all
		String res = MyUtils.suRun(null);
		if (!res.equals("")) {
			throw new Exception(res);
		}
		res = MyUtils.suRunScript(getContext(), null, R.raw.fix_battery);
		if (!res.equals("")) {
			throw new Exception(res);
		}
	}

	static public void fixBattd() throws Exception {
		if (MyUtils.findBusybox() == null) {
			throw new Exception(getContext().getText(R.string.err_busybox).toString());
		}
		// test run, to see if we can su at all
		String res = MyUtils.suRun(null);
		if (!res.equals("")) {
			throw new Exception(res);
		}
		res = MyUtils.suRunScript(getContext(), null, R.raw.fix_battd);
		if (!res.equals("")) {
			throw new Exception(res);
		}
	}

	static public void reboot() {
		class Temp {
			public ProgressDialog pd = null;
			public Exception ex = null;

			public void show() {
				if (pd != null) {
					return;
				}
				pd = ProgressDialog.show(ctx, "", ctx.getText(R.string.msg_rebooting), true, false);
				pd.setCancelable(false);
			}

			public void hide() {
				if (pd == null) {
					return;
				}
				pd.dismiss();
				pd = null;
			}

			public void error() {
				if (ex == null) {
					return;
				}
				String msg = String.format(ctx.getText(R.string.err_reboot).toString(), ex.getMessage());
				// FIXME: toast doesn't work for some reason, possibly because it's called from a thread
				//Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
				showNotification(msg);
			}
		}
		final Temp data = new Temp();
		try {
			MyUtils.rebootApi(getContext(), null);
		}
		catch (Exception ex) {
			data.show();
			try {
				Thread th = new Thread(new Runnable() {
					@Override
					public void run() {
						Looper.prepare();
						try {
							String ret = MyUtils.suRunScript(getContext(), null, R.raw.reboot);
							if (!ret.equals("")) {
								throw new Exception(ret);
							}
						}
						catch (Exception ex) {
							try {
								MyUtils.rebootCommand(null);
							}
							catch (Exception ex2) {
								data.ex = ex2;
								data.hide();
								data.error();
							}
						}
						Looper.loop();
						Looper.myLooper().quit();
					}
				});
				th.setDaemon(false);
				th.start();
			}
			catch (Exception ex2) {
				data.ex = ex2;
				data.hide();
				data.error();
			}
		}
	}

	static protected Context getContext() {
		Context c = ctx.getApplicationContext();
		return (c == null) ? ctx : c;
	}

	static public void monStart() {
		ctx.startService(new Intent(ctx, MonitorService.class));
	}

	static public void monStop() {
		ctx.stopService(new Intent(ctx, MonitorService.class));
	}

	static public boolean monRunning() {
		Context c = getContext();
		ActivityManager manager = (ActivityManager)c.getSystemService(Activity.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo svc : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (MonitorService.class.getName().equals(svc.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	static public void monCondStart() {
		if (!monRunning()) {
			monStart();
		}
	}

	static public void monCondStop() {
		if (monRunning()) {
			monStop();
		}
	}
}
