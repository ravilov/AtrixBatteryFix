package hr.ravilov.atrixbatteryfix;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.zip.CRC32;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.widget.Toast;
import hr.ravilov.atrixbatteryfix.MyUtils;
import hr.ravilov.atrixbatteryfix.Settings;
import hr.ravilov.atrixbatteryfix.MonitorService;
import hr.ravilov.atrixbatteryfix.R;

public class BatteryFix {
	static public final String CHARGING_FILE = "/sys/class/power_supply/battery/disable_charging";

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
				(mProgress > 0) ? MyUtils.getContext().getText(mProgress).toString() : null,
				(mSuccess > 0) ? MyUtils.getContext().getText(mSuccess).toString() : null,
				(mError > 0) ? MyUtils.getContext().getText(mError).toString() : null
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
			pd = ProgressDialog.show(MyUtils.getContext(), "", (msg_progress == null) ? "" : msg_progress, true, false);
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
			Toast.makeText(MyUtils.getContext(), msg_success, Toast.LENGTH_SHORT).show();
		}

		public void error() {
			if (ex == null) {
				return;
			}
			if (msg_error == null) {
				return;
			}
			Toast.makeText(MyUtils.getContext(), String.format(msg_error, ex.getMessage()), Toast.LENGTH_LONG).show();
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

	static enum HasCharging {
		UNKNOWN,
		NO,
		YES
	};
	static private boolean triggered;
	static private NotificationManager nm;
	static private HasCharging hasCharging = HasCharging.UNKNOWN;

	static public void init(boolean trig) {
		triggered = trig;
		nm = (NotificationManager)MyUtils.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
	}

	static public boolean run() {
		Settings.init();
		try {
			if (Settings.showNotifications) {
				int msgId = triggered ? R.string.msg_autocalibrate : R.string.msg_calibrate;
				showNotification(MyUtils.getContext().getText(msgId).toString());
			}
			recalibrate();
			if (triggered && Settings.showNotifications) {
				int msgId = (Settings.autoAction != Settings.AutoAction.NONE) ? R.string.msg_done : R.string.msg_done_reminder;
				Toast.makeText(MyUtils.getContext(), MyUtils.getContext().getText(msgId).toString(), Toast.LENGTH_LONG).show();
			}
		}
		catch (Exception ex) {
			String msg = ex.getMessage();
			if (msg == null || msg.equals("")) {
				msg = ex.toString();
			}
			Toast.makeText(MyUtils.getContext(), String.format(MyUtils.getContext().getText(R.string.err_calibrate).toString(), msg), Toast.LENGTH_LONG).show();
			return false;
		}
		return true;
	}

	static public boolean fix() {
		Settings.init();
		try {
			if (Settings.showNotifications) {
				showNotification(MyUtils.getContext().getText(R.string.msg_fixing).toString());
			}
			fixBattd();
			if (Settings.showNotifications) {
				Toast.makeText(MyUtils.getContext(), MyUtils.getContext().getText(R.string.msg_fixed).toString(), Toast.LENGTH_SHORT).show();
			}
		}
		catch (Exception ex) {
			String msg = ex.getMessage();
			if (msg == null || msg.equals("")) {
				msg = ex.toString();
			}
			Toast.makeText(MyUtils.getContext(), String.format(MyUtils.getContext().getText(R.string.err_fix).toString(), msg), Toast.LENGTH_LONG).show();
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
			ntf.setLatestEventInfo(MyUtils.getContext(), null, null, PendingIntent.getActivity(MyUtils.getContext(), 0, new Intent(), 0));
		}
		catch (Exception ex) { }
		nm.notify(id, ntf);
		nm.cancel(id);
	}

	static public void recalibrate() throws Exception {
		if (MyUtils.shFind() == null) {
			throw new Exception(MyUtils.getContext().getText(R.string.err_shell).toString());
		}
		// test run, to see if we can su at all
		String res = MyUtils.suRun(null);
		if (!res.equals("")) {
			throw new Exception(res);
		}
		res = MyUtils.suRunScript(null, R.raw.fix_battery);
		if (!res.equals("")) {
			throw new Exception(res);
		}
	}

	static public void fixBattd() throws Exception {
		if (MyUtils.shFind() == null) {
			throw new Exception(MyUtils.getContext().getText(R.string.err_shell).toString());
		}
		// test run, to see if we can su at all
		String res = MyUtils.suRun(null);
		if (!res.equals("")) {
			throw new Exception(res);
		}
		res = MyUtils.suRunScript(null, R.raw.fix_battd);
		if (!res.equals("")) {
			throw new Exception(res);
		}
		if (Settings.battstats) {
			res = MyUtils.suRunScript(null, R.raw.del_battstats);
			if (!res.equals("")) {
				throw new Exception(res);
			}
		}
	}

	static public void restartBattd() {
		(new ProgressRunner(R.string.msg_restarting, R.string.msg_restart_done, R.string.err_restart) {
			public void onRun() throws Exception {
				if (MyUtils.shFind() == null) {
					throw new Exception(MyUtils.getContext().getText(R.string.err_shell).toString());
				}
				// test run, to see if we can su at all
				String res = MyUtils.suRun(null);
				if (!res.equals("")) {
						throw new Exception(res);
				}
				res = MyUtils.suRunScript(null, R.raw.restart_battd);
				if (!res.equals("")) {
					throw new Exception(res);
				}
			}
		}).run();
	}

	static public void reboot() {
		try {
			MyUtils.rebootApi(null);
		}
		catch (Exception ex) {
			(new ProgressRunner(R.string.msg_rebooting, -1, R.string.err_reboot) {
				public void onRun() throws Exception {
					try {
						String ret = MyUtils.suRunScript(null, R.raw.reboot);
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

	static public boolean canCharging() {
		if (hasCharging == HasCharging.UNKNOWN) {
			try {
				if (hasCharging != HasCharging.YES) {
					File f = new File(CHARGING_FILE);
					if (f.exists()) {
						hasCharging = HasCharging.YES;
					}
				}
				if (hasCharging != HasCharging.YES) {
					String res = MyUtils.suRun(null, String.format("ls '%s'", CHARGING_FILE));
					if (res.equals(CHARGING_FILE)) {
						hasCharging = HasCharging.YES;
					}
				}
				hasCharging = HasCharging.NO;
			}
			catch (Exception ex) {
				hasCharging = HasCharging.NO;
			}
		}
		return (hasCharging == HasCharging.YES) ? true : false;
	}

	static public void setCharging(boolean enabled) {
		try {
			if (MyUtils.shFind() == null) {
				throw new Exception(MyUtils.getContext().getText(R.string.err_shell).toString());
			}
			// test run, to see if we can su at all
			String res = MyUtils.suRun(null);
			if (!res.equals("")) {
					throw new Exception(res);
			}
			res = MyUtils.suRun(null, String.format("echo %d > %s", enabled ? 0 : 1, CHARGING_FILE));
			if (!res.equals("")) {
				throw new Exception(res);
			}
		}
		catch (Exception ex) { }
	}

	static public boolean getCharging() {
		try {
			File f = new File(CHARGING_FILE);
			if (f.exists() && f.canRead()) {
				try {
					BufferedReader in = new BufferedReader(new FileReader(f));
					Integer v = Integer.valueOf(in.readLine().trim());
					return (v == 0) ? false : true;
				}
				catch (Exception ex) { }
			}
			if (MyUtils.shFind() == null) {
				throw new Exception(MyUtils.getContext().getText(R.string.err_shell).toString());
			}
			// test run, to see if we can su at all
			String res = MyUtils.suRun(null);
			if (!res.equals("")) {
					throw new Exception(res);
			}
			res = MyUtils.suRun(null, String.format("cat %s", CHARGING_FILE));
			if (res != null) {
				if (res.equals("0")) {
					return false;
				}
				if (res.equals("1")) {
					return true;
				}
				if (!res.equals("")) {
					throw new Exception(res);
				}
			}
		}
		catch (Exception ex) { }
		return true;
	}

	static public void monStart() {
		MyUtils.getContext().startService(new Intent(MyUtils.getContext(), MonitorService.class));
	}

	static public void monStop() {
		MyUtils.getContext().stopService(new Intent(MyUtils.getContext(), MonitorService.class));
	}

	static public boolean monRunning() {
		Context c = MyUtils.getContext();
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

	static public void checkPower(boolean isThread) {
		if (!BatteryInfo.isOnPower || BatteryInfo.isDischarging) {
			BatteryFix.monCondStop();
			return;
		}
		if (isThread) {
			Looper.prepare();
		}
		try {
			if (Settings.noUsbCharging) {
				BatteryFix.setCharging((BatteryInfo.isCharging && BatteryInfo.isOnUSB) ? false : true);
			}
		}
		catch (Exception ex) { }
		if (Settings.autoFix) {
			BatteryFix.run();
		}
		BatteryInfo.refresh();
		if (Settings.autoAction != Settings.AutoAction.NONE && !(BatteryInfo.isFull || BatteryInfo.seemsFull)) {
			BatteryFix.monCondStart();
		} else {
			BatteryFix.monCondStop();
		}
		if (isThread) {
			Looper.loop();
			Looper.myLooper().quit();
		}
	}
}
