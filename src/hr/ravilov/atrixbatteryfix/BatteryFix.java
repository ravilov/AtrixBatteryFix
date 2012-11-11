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
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class BatteryFix {
	static public final String CHARGING_FILE = "/sys/class/power_supply/battery/disable_charging";

	static enum HasCharging {
		UNKNOWN,
		NO,
		YES
	};
	private boolean triggered;
	private NotificationManager nm;
	private HasCharging hasCharging = HasCharging.UNKNOWN;
	private ShellInterface shell = null;
	private boolean siTried = false;
	private MyUtils utils;
	private BatteryInfo info;
	private Settings settings;

	public BatteryFix(MyUtils u, Settings set, BatteryInfo bi, boolean trig) {
		triggered = trig ? true : false;
		utils = u;
		settings = set;
		info = bi;
		nm = (NotificationManager)utils.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
	}

	protected void showError(int msgId, Exception ex) {
		String msg = ex.getMessage();
		if (msg == null || msg.equals("")) {
			msg = ex.toString();
		}
		if (msg != null && !msg.equals("")) {
			Toast.makeText(utils.getContext(), String.format(utils.getContext().getText(msgId).toString(), msg), Toast.LENGTH_LONG).show();
		}
	}

	public boolean run() {
		try {
			if (settings.prefNotifications()) {
				int msgId = triggered ? R.string.msg_autofix : R.string.msg_fix;
				showNotification(utils.getContext().getText(msgId).toString());
			}
			fixBattery();
			if (triggered && settings.prefNotifications()) {
				int msgId = (settings.prefAutoAction() != Settings.AutoAction.NONE) ? R.string.msg_done : R.string.msg_done_reminder;
				Toast.makeText(utils.getContext(), utils.getContext().getText(msgId).toString(), Toast.LENGTH_LONG).show();
			}
		}
		catch (Exception ex) {
			if (triggered) {
				showNotification(utils.getContext().getText(R.string.err_fix_short).toString());
			} else {
				showError(R.string.err_fix, ex);
			}
			return false;
		}
		return true;
	}

	public boolean fix() {
		try {
			if (settings.prefNotifications()) {
				showNotification(utils.getContext().getText(R.string.msg_fixing).toString());
			}
			fixBattd();
			if (settings.prefNotifications()) {
				Toast.makeText(utils.getContext(), utils.getContext().getText(R.string.msg_fixed).toString(), Toast.LENGTH_SHORT).show();
			}
		}
		catch (Exception ex) {
			if (triggered) {
				showNotification(utils.getContext().getText(R.string.err_battd_short).toString());
			} else {
				showError(R.string.err_battd, ex);
			}
			return false;
		}
		return true;
	}

	public int showNotification(String str, Intent i, String title, String text) {
		CRC32 gen = new CRC32();
		gen.update(str.getBytes());
		int id = (int)gen.getValue();
		nm.cancel(id);
		Notification ntf = new Notification(R.drawable.icon, str, System.currentTimeMillis());
		if (i == null) {
			ntf.flags |= Notification.FLAG_AUTO_CANCEL;
		} else {
			ntf.flags |= Notification.FLAG_ONGOING_EVENT;
		}
		try {
			ntf.setLatestEventInfo(
				utils.getContext(),
				title,
				text,
				PendingIntent.getActivity(
					utils.getContext(),
					0,
					(i == null) ? new Intent() : i,
					PendingIntent.FLAG_UPDATE_CURRENT
				)
			);
		}
		catch (Exception ex) { }
		nm.notify(id, ntf);
		if (i == null) {
			hideNotification(id);
		}
		return id;
	}

	public int showNotification(String str) {
		return showNotification(str, null, null, null);
	}

	public void hideNotification(int id) {
		try {
			nm.cancel(id);
		}
		catch (Exception ex) { }
	}

	public void fixBattery() throws Exception {
		if (utils.shFind() == null) {
			throw new Exception(utils.getContext().getText(R.string.err_shell).toString());
		}
		// test run, to see if we can su at all
		String res = utils.suRun(null);
		if (!res.equals("")) {
			throw new Exception(res);
		}
		res = utils.suRunScript(null, R.raw.fix_battery);
		if (!res.equals("")) {
			throw new Exception(res);
		}
	}

	public void fixBattd() throws Exception {
		if (utils.shFind() == null) {
			throw new Exception(utils.getContext().getText(R.string.err_shell).toString());
		}
		// test run, to see if we can su at all
		String res = utils.suRun(null);
		if (!res.equals("")) {
			throw new Exception(res);
		}
		res = utils.suRunScript(null, R.raw.fix_battd);
		if (!res.equals("")) {
			throw new Exception(res);
		}
		if (settings.prefBattStats()) {
			res = utils.suRunScript(null, R.raw.del_battstats);
			if (!res.equals("")) {
				throw new Exception(res);
			}
		}
	}

	private void _restartAction() throws Exception {
		if (utils.shFind() == null) {
			throw new Exception(utils.getContext().getText(R.string.err_shell).toString());
		}
		// test run, to see if we can su at all
		String res = utils.suRun(null);
		if (!res.equals("")) {
			throw new Exception(res);
		}
		res = utils.suRunScript(null, R.raw.restart_battd);
		if (!res.equals("")) {
			throw new Exception(res);
		}
		checkPower();
	}

	public void restartBattd() {
		if (triggered) {
			if (settings.prefNotifications()) {
				showNotification(utils.getContext().getText(R.string.msg_restarting).toString());
			}
			try {
				_restartAction();
			}
			catch (Exception ex) {
				if (settings.prefNotifications()) {
					showNotification(utils.getContext().getText(R.string.err_restart_short).toString());
				}
			}
		} else {
			(new ProgressRunner(utils, R.string.msg_restarting, R.string.msg_restart_done, R.string.err_restart) {
				public void onRun() throws Exception {
					_restartAction();
				}
			}).run();
		}
	}

	private void _rebootAction() throws Exception {
		try {
			String ret = utils.suRunScript(null, R.raw.reboot);
			if (!ret.equals("")) {
				throw new Exception(ret);
			}
		}
		catch (Exception ex) {
			utils.rebootCommand(null);
		}
	}

	public void reboot() {
		try {
			utils.rebootApi(null);
		}
		catch (Exception ex) {
			if (triggered) {
				if (settings.prefNotifications()) {
					showNotification(utils.getContext().getText(R.string.msg_autoreboot).toString());
				}
				try {
					_rebootAction();
				}
				catch (Exception ex2) {
					if (settings.prefNotifications()) {
						showNotification(utils.getContext().getText(R.string.err_reboot_short).toString());
					}
				}
			} else {
				(new ProgressRunner(utils, R.string.msg_rebooting, -1, R.string.err_reboot) {
					public void onRun() throws Exception {
						_rebootAction();
					}
				}).run();
			}
		}
	}

	public void monStart() {
		utils.log("starting battmon service");
		utils.getContext().startService(new Intent(utils.getContext(), MonitorService.class));
	}

	public void monStop() {
		utils.log("stopping battmon service");
		utils.getContext().stopService(new Intent(utils.getContext(), MonitorService.class));
	}

	public boolean monRunning() {
		Context c = utils.getContext();
		ActivityManager manager = (ActivityManager)c.getSystemService(Activity.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo svc : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (MonitorService.class.getName().equals(svc.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	public void monCondStart() {
		if (!monRunning()) {
			monStart();
		}
	}

	public void monCondStop() {
		if (monRunning()) {
			monStop();
		}
	}

	public void setupCharging() throws Exception {
		if (shell != null) {
			return;
		}
		shell = new ShellInterface(utils, utils.suFind());
	}

	public void setupChargingForUI() {
		try {
			setupCharging();
		}
		catch (Exception ex) {
			showError(R.string.err_su, ex);
		}
		siTried = true;
	}

	public void cleanupCharging() {
		if (shell != null) {
			try {
				shell.close();
			}
			catch (Exception ex) { }
		}
		shell = null;
		siTried = false;
	}

	public boolean canCharging() {
		if (shell == null && siTried) {
			hasCharging = HasCharging.NO;
		}
		if (hasCharging != HasCharging.UNKNOWN) {
			return (hasCharging == HasCharging.YES) ? true : false;
		}
		if (hasCharging == HasCharging.UNKNOWN) {
			try {
				File f = new File(CHARGING_FILE);
				if (f.exists()) {
					hasCharging = HasCharging.YES;
				}
			}
			catch (Exception ex) { }
		}
		if (hasCharging == HasCharging.UNKNOWN && (shell != null || !siTried)) {
			try {
				String cmd = String.format("echo '%s'*", CHARGING_FILE);
				String res = (shell == null) ? utils.suRun(null, cmd) : shell.sendAndReceive(cmd);
				if (res.equals(CHARGING_FILE)) {
					hasCharging = HasCharging.YES;
				}
			}
			catch (Exception ex) { }
		}
		if (hasCharging == HasCharging.UNKNOWN) {
			hasCharging = HasCharging.NO;
		}
		return (hasCharging == HasCharging.YES) ? true : false;
	}

	public void setCharging(boolean enabled) throws Exception {
		if (shell == null && siTried) {
			return;
		}
		try {
			String cmd = String.format("echo %d > '%s'", enabled ? 0 : 1, CHARGING_FILE);
			if (shell == null) {
				if (utils.shFind() == null) {
					throw new Exception(utils.getContext().getText(R.string.err_shell).toString());
				}
				// test run, to see if we can su at all
				String res = utils.suRun(null);
				if (!res.equals("")) {
						throw new Exception(res);
				}
			}
			String res = null;
			Exception ex = null;
			try {
				res = (shell == null) ? utils.suRun(null, cmd) : (shell.sendCommand(cmd) ? "" : "Error running shell command");
			}
			catch (Exception _ex) {
				ex = _ex;
				res = null;
			}
			if (shell != null && !shell.isProcessAlive() && !siTried) {
				try {
					shell.close();
				}
				catch (Exception _ex) { }
				siTried = true;
			}
			if (ex != null) {
				throw ex;
			}
			if ((res == null && ex != null) || (res != null && !res.equals(""))) {
				throw (res == null) ? ex : new Exception(res);
			}
		}
		catch (Exception ex) {
			if (shell != null && !triggered) {
				try {
					shell.close();
				}
				catch (Exception _ex) { }
				shell = null;
				siTried = true;
				showError(R.string.err_su, ex);
			} else {
				throw ex;
			}
		}
	}

	public boolean getCharging() throws Exception {
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
			String cmd = String.format("cat '%s'", CHARGING_FILE);
			if (shell == null && !siTried) {
				if (utils.shFind() == null) {
					throw new Exception(utils.getContext().getText(R.string.err_shell).toString());
				}
				// test run, to see if we can su at all
				String test = utils.suRun(null);
				if (!test.equals("")) {
					throw new Exception(test);
				}
			}
			if (shell == null && siTried) {
				res = "";
			} else {
				res = (shell == null) ? utils.suRun(null, cmd) : shell.sendAndReceive(cmd);
			}
		}
		catch (Exception ex) {
			if (shell != null && !triggered) {
				try {
					shell.close();
				}
				catch (Exception _ex) { }
				shell = null;
				siTried = true;
				showError(R.string.err_su, ex);
			} else {
				throw ex;
			}
		}
		if (res != null && !res.equals("")) {
			try {
				Integer v = Integer.valueOf(res);
				return (v == 0) ? true : false;
			}
			catch (Exception ex) { }
		}
		return true;
	}

	public void setUsbCharging(boolean enabled) {
		try {
			if (enabled == getCharging()) {
				return;
			}
			if (enabled) {
				setCharging(true);
				if (settings.prefNotifications()) {
					showNotification(utils.getContext().getText(R.string.msg_usb_enabled).toString());
				}
			} else {
				setCharging(false);
				if (settings.prefNotifications()) {
					showNotification(utils.getContext().getText(R.string.msg_usb_disabled).toString());
				}
			}
		}
		catch (Exception ex) {
			showError(R.string.err_charging, ex);
		}
	}

	public void checkPower() {
		if (settings.prefNoUsbCharging() && canCharging()) {
			setUsbCharging(info.isOnUSB ? false : true);
		}
		boolean isCharging = (
			info.isOnPower &&
			!info.isDischarging &&
			(
				!info.isOnUSB ||
				!settings.prefNoUsbCharging() ||
				!canCharging()
			)
		) ? true : false;
		if (isCharging) {
			if (settings.prefAutoFix() && triggered) {
				run();
			}
			if (!(info.isFull || info.seemsFull)) {
				switch (settings.prefAutoAction()) {
					case REBOOT:
					case RESTART:
						monCondStart();
						break;
					case NONE:
					default:
						monCondStop();
						break;
				}
			}
		} else {
			monCondStop();
		}
	}
}
