package hr.ravilov.atrixbatteryfix;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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
			pd = ProgressDialog.show(MyUtils.getBaseContext(), "", (msg_progress == null) ? "" : msg_progress, true, false);
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
			Toast.makeText(MyUtils.getBaseContext(), msg_success, Toast.LENGTH_SHORT).show();
		}

		public void error() {
			if (ex == null) {
				return;
			}
			if (msg_error == null) {
				return;
			}
			Toast.makeText(MyUtils.getBaseContext(), String.format(msg_error, ex.getMessage()), Toast.LENGTH_LONG).show();
		}

		public void run() {
			final Activity act = (Activity)MyUtils.getBaseContext();
			act.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					show();
				}
			});
			try {
				Thread th = new Thread(new Runnable() {
					@Override
					public void run() {
						Looper.prepare();
						ex = null;
						try {
							onRun();
						}
						catch (Exception ex2) {
							ex = ex2;
						}
						act.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								hide();
								if (ex == null) {
									success();
								} else {
									error();
								}
							}
						});
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

	static protected void showError(int msgId, Exception ex) {
		String msg = ex.getMessage();
		if (msg == null || msg.equals("")) {
			msg = ex.toString();
		}
		Toast.makeText(MyUtils.getContext(), String.format(MyUtils.getContext().getText(msgId).toString(), msg), Toast.LENGTH_LONG).show();
	}

	static public boolean run() {
		try {
			if (Settings.prefNotifications()) {
				int msgId = triggered ? R.string.msg_autofix : R.string.msg_fix;
				showNotification(MyUtils.getContext().getText(msgId).toString());
			}
			fixBattery();
			if (triggered && Settings.prefNotifications()) {
				int msgId = (Settings.prefAutoAction() != Settings.AutoAction.NONE) ? R.string.msg_done : R.string.msg_done_reminder;
				Toast.makeText(MyUtils.getContext(), MyUtils.getContext().getText(msgId).toString(), Toast.LENGTH_LONG).show();
			}
		}
		catch (Exception ex) {
			if (triggered) {
				showNotification(MyUtils.getContext().getText(R.string.err_fix_short).toString());
			} else {
				showError(R.string.err_fix, ex);
			}
			return false;
		}
		return true;
	}

	static public boolean fix() {
		try {
			if (Settings.prefNotifications()) {
				showNotification(MyUtils.getContext().getText(R.string.msg_fixing).toString());
			}
			fixBattd();
			if (Settings.prefNotifications()) {
				Toast.makeText(MyUtils.getContext(), MyUtils.getContext().getText(R.string.msg_fixed).toString(), Toast.LENGTH_SHORT).show();
			}
		}
		catch (Exception ex) {
			if (triggered) {
				showNotification(MyUtils.getContext().getText(R.string.err_battd_short).toString());
			} else {
				showError(R.string.err_battd, ex);
			}
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

	static public void fixBattery() throws Exception {
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
		if (Settings.prefBattStats()) {
			res = MyUtils.suRunScript(null, R.raw.del_battstats);
			if (!res.equals("")) {
				throw new Exception(res);
			}
		}
	}

	static private void _restartAction() throws Exception {
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

	static public void restartBattd() {
		if (triggered) {
			if (Settings.prefNotifications()) {
				showNotification(MyUtils.getContext().getText(R.string.msg_restarting).toString());
			}
			try {
				_restartAction();
			}
			catch (Exception ex) {
				if (Settings.prefNotifications()) {
					showNotification(MyUtils.getContext().getText(R.string.err_restart_short).toString());
				}
			}
		} else {
			(new ProgressRunner(R.string.msg_restarting, R.string.msg_restart_done, R.string.err_restart) {
				public void onRun() throws Exception {
					_restartAction();
				}
			}).run();
		}
	}

	static private void _rebootAction() throws Exception {
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

	static public void reboot() {
		try {
			MyUtils.rebootApi(null);
		}
		catch (Exception ex) {
			if (triggered) {
				if (Settings.prefNotifications()) {
					showNotification(MyUtils.getContext().getText(R.string.msg_autoreboot).toString());
				}
				try {
					_rebootAction();
				}
				catch (Exception ex2) {
					if (Settings.prefNotifications()) {
						showNotification(MyUtils.getContext().getText(R.string.err_reboot_short).toString());
					}
				}
			} else {
				(new ProgressRunner(R.string.msg_rebooting, -1, R.string.err_reboot) {
					public void onRun() throws Exception {
						_rebootAction();
					}
				}).run();
			}
		}
	}

	static public boolean canCharging() {
		if (hasCharging != HasCharging.UNKNOWN) {
			return (hasCharging == HasCharging.YES) ? true : false;
		}
		try {
			File f = new File(CHARGING_FILE);
			if (f.exists()) {
				hasCharging = HasCharging.YES;
				throw new IOException("done");
			}
			String res = MyUtils.suRun(null, String.format("echo '%s'*", CHARGING_FILE));
			if (res.equals(CHARGING_FILE)) {
				hasCharging = HasCharging.YES;
				throw new IOException("done");
			}
			hasCharging = HasCharging.NO;
		}
		catch (IOException ex) {
			// ignore
		}
		catch (Exception ex) {
			hasCharging = HasCharging.NO;
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
		String res = null;
		try {
			File f = new File(CHARGING_FILE);
			if (f.exists() && f.canRead()) {
				try {
					BufferedReader in = new BufferedReader(new FileReader(f));
					res = in.readLine().trim();
				}
				catch (Exception ex) { }
			}
			if (MyUtils.shFind() == null) {
				throw new Exception(MyUtils.getContext().getText(R.string.err_shell).toString());
			}
			// test run, to see if we can su at all
			String test = MyUtils.suRun(null);
			if (!test.equals("")) {
				throw new Exception(test);
			}
			res = MyUtils.suRun(null, String.format("cat '%s'", CHARGING_FILE));
		}
		catch (Exception ex) { }
		if (res != null && !res.equals("")) {
			try {
				Integer v = Integer.valueOf(res);
				return (v == 0) ? true : false;
			}
			catch (Exception ex) { }
		}
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
		Settings.load();
		BatteryInfo.refresh();
		if (!BatteryInfo.isOnPower || BatteryInfo.isDischarging) {
			BatteryFix.monCondStop();
			return;
		}
		if (isThread) {
			Looper.prepare();
		}
		if (Settings.prefAutoFix()) {
			run();
		}
		if (Settings.prefNoUsbCharging() && BatteryFix.canCharging()) {
			try {
				if (BatteryInfo.isCharging && BatteryInfo.isOnUSB) {
					if (BatteryFix.getCharging()) {
						setCharging(false);
						if (Settings.prefNotifications()) {
							showNotification(MyUtils.getContext().getText(R.string.msg_usb_disabled).toString());
						}
					}
				} else {
					if (!BatteryFix.getCharging()) {
						setCharging(true);
						if (Settings.prefNotifications()) {
							showNotification(MyUtils.getContext().getText(R.string.msg_usb_enabled).toString());
						}
					}
				}
			}
			catch (Exception ex) {
				showError(R.string.err_charging, ex);
			}
		}
		if (Settings.prefAutoAction() != Settings.AutoAction.NONE && !(BatteryInfo.isFull || BatteryInfo.seemsFull)) {
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
