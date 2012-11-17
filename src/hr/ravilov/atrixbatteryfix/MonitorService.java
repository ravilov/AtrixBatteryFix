package hr.ravilov.atrixbatteryfix;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.widget.Toast;

public class MonitorService extends Service {
	private Thread th;
	private volatile boolean thTerminate;
	private MyUtils utils;
	private BatteryInfo info;
	private BatteryFix fix;
	private Settings settings;
	private int notificationId = -1;
	private BroadcastReceiver br = null;

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public void onStart(Intent i, int startId) {
		super.onStart(i, startId);
		Thread.setDefaultUncaughtExceptionHandler(new MyExceptionCatcher());
		start();
	}

	public int onStartCommand(Intent i, int flags, int startId) {
		start();
		return 1;	/* START_STICKY */
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stop();
	}

	@Override
	public void onLowMemory() {
	}

	protected Runnable makeRunner() {
		return new Runnable() {
			private volatile boolean actionDone = false;
			private Service svc = MonitorService.this;

			private void action() {
				if (actionDone) {
					return;
				}
				synchronized (svc) {
					actionDone = true;
				}
				settings.load();
				if (settings.prefNon100() && Integer.valueOf(info.battShown) >= 100) {
					return;
				}
				utils.log("performing auto-action");
				if (settings.prefAutoFix() || true) {
					try {
						fix.fixBattery();
					}
					catch (Exception ex) { }
				}
				switch (settings.prefAutoAction()) {
					case REBOOT: {
							fix.reboot();
						}
						break;
					case RESTART: {
							fix.restartBattd();
						}
						break;
					case NONE:
					default:
						break;
				}
				utils.log("auto-action done");
			}

			private void addFilter() {
				if (br != null) {
					return;
				}
				synchronized (svc) {
					IntentFilter f = new IntentFilter();
					f.addAction(Intent.ACTION_BATTERY_CHANGED);
					br = new BroadcastReceiver() {
						@Override
						public void onReceive(Context c, Intent i) {
							info.refresh(i);
							if (info.isOnPower && (info.isFull || info.seemsFull)) {
								action();
							}
							Thread.currentThread().interrupt();
						}
					};
					utils.log("registering receiver");
					registerReceiver(br, f);
				}
			}

			private void delFilter() {
				if (br == null) {
					return;
				}
				synchronized (svc) {
					utils.log("unregistering receiver");
					unregisterReceiver(br);
					br = null;
				}
			}

			@Override
			public void run() {
				Thread.setDefaultUncaughtExceptionHandler(new MyExceptionCatcher());
				synchronized (svc) {
					actionDone = false;
				}
				addFilter();
				utils.log("entering main service loop");
				while (info.isOnPower && !info.isFull && !thTerminate) {
					try {
						Thread.sleep(60 * 1000);
					}
					catch (Exception ex) { }
					info.refresh();
				}
				utils.log("exiting main service loop");
				delFilter();
				if (info.isOnPower && actionDone) {
					utils.log("settling down");
					try {
						Thread.sleep(5 * 1000);
					}
					catch (Exception ex) { }
				}
				try {
					if (notificationId > 0) {
						fix.hideNotification(notificationId);
						notificationId = -1;
					}
				}
				catch (Exception ex) { }
			}
		};
	}

	protected void startThread() throws Exception {
		th = new Thread(makeRunner());
		synchronized (this) {
			thTerminate = false;
		}
		th.setDaemon(true);
		th.start();
	}

	protected void stopThread() throws Exception {
		if (br != null) {
			synchronized (this) {
				utils.log("unregistering receiver");
				unregisterReceiver(br);
				br = null;
			}
		}
		synchronized (this) {
			thTerminate = true;
		}
		try {
			th.interrupt();
			th.join();
		}
		catch (InterruptedException ex) { }
	}

	protected void start() {
		if (th != null) {
			return;
		}
		try {
			utils = new MyUtils(this);
			info = new BatteryInfo(utils);
			if (info.isFull) {
				return;
			}
			settings = (new Settings()).init(utils);
			fix = new BatteryFix(utils, settings, info, true);
			setForeground(true);
			startThread();
			if (settings.prefNotifications()) {
				if (notificationId > 0) {
					fix.hideNotification(notificationId);
				}
				notificationId = fix.showNotification(
					getString(R.string.msg_start),
					new Intent(this, MainActivity.class),
					getString(R.string.msg_running),
					getString(R.string.msg_running_)
				);
			}
		}
		catch (Exception ex) {
			String msg = ex.getMessage();
			if (msg == null || msg.equals("")) {
				msg = ex.toString();
			}
			Toast.makeText(this, String.format(getString(R.string.err_start), msg), Toast.LENGTH_LONG).show();
		}
	}
	
	protected void stop() {
		if (th == null) {
			return;
		}
		try {
			stopThread();
			if (settings.prefNotifications()) {
				if (notificationId > 0) {
					try {
						fix.hideNotification(notificationId);
					}
					catch (Exception ex) { }
					notificationId = -1;
				}
				fix.showNotification(getString(R.string.msg_stop));
			} else {
				notificationId = -1;
			}
		}
		catch (Exception ex) {
			String msg = ex.getMessage();
			if (msg == null || msg.equals("")) {
				msg = ex.toString();
			}
			Toast.makeText(this, String.format(getString(R.string.err_stop), msg), Toast.LENGTH_LONG).show();
		}
		th = null;
	}
}
