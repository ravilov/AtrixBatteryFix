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
	static public final String
		PREF_AUTOFIX = "enabled",
		PREF_AUTOACTION = "autoAction",
		PREF_AUTOACTION_REBOOT = "autoActionReboot",
		PREF_AUTOACTION_RESTART = "autoActionRestart",
		PREF_NOTIFICATIONS = "notifications",
		PREF_SHOWABOUT = "showAbout",
		// for backward compatibility
		PREF_AUTOREBOOT = "autoReboot"
	;

	static abstract class ProgressRunner {
		public ProgressDialog pd = null;
		public Exception ex = null;
		private String msg_progress;
		private String msg_success;
		private String msg_error;

		abstract public void onRun() throws Exception;

		public ProgressRunner(String mProgress, String mSuccess, String mError) {
			init(mProgress, mSuccess, mError);
		}

		public ProgressRunner(int mProgress, int mSuccess, int mError) {
			init(
				(mProgress > 0) ? ctx.getText(mProgress).toString() : null,
				(mSuccess > 0) ? ctx.getText(mSuccess).toString() : null,
				(mError > 0) ? ctx.getText(mError).toString() : null
			);
		}

		private void init(String mProgress, String mSuccess, String mError) {
			msg_progress = mProgress;
			msg_success = mSuccess;
			msg_error = mError;
		}

		public void show() {
			if (pd != null) {
				return;
			}
			pd = ProgressDialog.show(ctx, "", (msg_progress == null) ? "" : msg_progress, true, false);
			pd.setCancelable(false);
		}

		public void hide() {
			if (pd == null) {
				return;
			}
			pd.dismiss();
			pd = null;
		}

		public void success() {
			if (msg_success == null) {
				return;
			}
			Toast.makeText(getContext(), msg_success, Toast.LENGTH_SHORT).show();
		}

		public void error() {
			if (ex == null) {
				return;
			}
			if (msg_error == null) {
				return;
			}
			Toast.makeText(getContext(), String.format(msg_error, ex.getMessage()), Toast.LENGTH_LONG).show();
		}

		public void run() {
			show();
			try {
				Thread th = new Thread(new Runnable() {
					@Override
					public void run() {
						Looper.prepare();
						try {
							onRun();
						}
						catch (Exception ex2) {
							ex = ex2;
							hide();
							error();
						}
						hide();
						success();
						Looper.loop();
						Looper.myLooper().quit();
					}
				});
				th.setDaemon(false);
				th.start();
			}
			catch (Exception ex2) {
				ex = ex2;
				hide();
				error();
			}
		}
	}

	private static Context ctx;
	private static boolean triggered;
	private static SharedPreferences prefs;
	public static boolean autoFix;
	public static boolean showNotifications;
	public static boolean autoAction;
	public static boolean autoActionReboot;
	public static boolean autoActionRestart;
	public static boolean showAbout;
	private static NotificationManager nm;

	static public void init(Context c, boolean trig) {
		ctx = c;
		triggered = trig;
		nm = (NotificationManager)getContext().getSystemService(Context.NOTIFICATION_SERVICE);
		prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		loadPrefs();
	}

	static public void loadPrefs() {
		autoFix = prefs.getBoolean(PREF_AUTOFIX, false);
		showNotifications = prefs.getBoolean(PREF_NOTIFICATIONS, true);
		if (prefs.getBoolean(PREF_AUTOREBOOT, false)) {
			autoAction = true;
			autoActionReboot = true;
			autoActionRestart = false;
		} else {
			autoAction = prefs.getBoolean(PREF_AUTOACTION, false);
			autoActionReboot = prefs.getBoolean(PREF_AUTOACTION_REBOOT, true);
			autoActionRestart = prefs.getBoolean(PREF_AUTOACTION_RESTART, false);
		}
		showAbout = prefs.getBoolean(PREF_SHOWABOUT, true);
	}

	static public void savePrefs() {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(PREF_AUTOFIX, autoFix);
		editor.putBoolean(PREF_NOTIFICATIONS, showNotifications);
		editor.putBoolean(PREF_AUTOACTION, autoAction);
		editor.putBoolean(PREF_AUTOACTION_REBOOT, autoActionReboot);
		editor.putBoolean(PREF_AUTOACTION_RESTART, autoActionRestart);
		editor.putBoolean(PREF_SHOWABOUT, showAbout);
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
				Toast.makeText(ctx, ctx.getText(autoAction ? R.string.msg_done : R.string.msg_done_reminder).toString(), Toast.LENGTH_LONG).show();
			}
		}
		catch (Exception ex) {
			String msg = ex.getMessage();
			if (msg == null || msg.equals("")) {
				msg = ex.toString();
			}
			Toast.makeText(ctx, String.format(ctx.getText(R.string.err_calibrate).toString(), msg), Toast.LENGTH_LONG).show();
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
			String msg = ex.getMessage();
			if (msg == null || msg.equals("")) {
				msg = ex.toString();
			}
			Toast.makeText(ctx, String.format(ctx.getText(R.string.err_fix).toString(), msg), Toast.LENGTH_LONG).show();
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
		if (MyUtils.shFind() == null) {
			throw new Exception(getContext().getText(R.string.err_shell).toString());
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
		if (MyUtils.shFind() == null) {
			throw new Exception(getContext().getText(R.string.err_shell).toString());
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

	static public void restartBattd() {
		(new ProgressRunner(R.string.msg_restarting, R.string.msg_restart_done, R.string.err_restart) {
			public void onRun() throws Exception {
				if (MyUtils.shFind() == null) {
					throw new Exception(getContext().getText(R.string.err_shell).toString());
				}
				// test run, to see if we can su at all
				String res = MyUtils.suRun(null);
				if (!res.equals("")) {
						throw new Exception(res);
				}
				res = MyUtils.suRunScript(getContext(), null, R.raw.restart_battd);
				if (!res.equals("")) {
					throw new Exception(res);
				}
			}
		}).run();
	}

	static public void reboot() {
		try {
			MyUtils.rebootApi(getContext(), null);
		}
		catch (Exception ex) {
			(new ProgressRunner(R.string.msg_rebooting, -1, R.string.err_reboot) {
				public void onRun() throws Exception {
					try {
						String ret = MyUtils.suRunScript(getContext(), null, R.raw.reboot);
						if (!ret.equals("")) {
							throw new Exception(ret);
						}
					}
					catch (Exception ex) {
						MyUtils.rebootCommand(null);
					}
				}
			}).run();
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
