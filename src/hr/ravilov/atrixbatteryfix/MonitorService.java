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
			synchronized (this) {
				thTerminate = false;
			}
			th = new Thread(new Runnable() {
				private BroadcastReceiver br;
				private volatile boolean actionDone = false;

				private void action() {
					if (actionDone) {
						return;
					}
					synchronized (this) {
						actionDone = true;
					}
					if (settings.prefAutoFix()) {
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
				}

				private void addFilter() {
					IntentFilter f = new IntentFilter();
					f.addAction(Intent.ACTION_BATTERY_CHANGED);
					br = new BroadcastReceiver() {
						@Override
						public void onReceive(Context c, Intent i) {
							info.refresh(i);
							if (info.isOnPower && (info.isFull || info.seemsFull)) {
								action();
							}
							if (info.isFull) {
								synchronized (MonitorService.this) {
									thTerminate = true;
								}
								Thread.currentThread().interrupt();
							}
						}
					};
					utils.log("registering receiver");
					registerReceiver(br, f);
				}

				private void delFilter() {
					utils.log("unregistering receiver");
					unregisterReceiver(br);
				}

				@Override
				public void run() {
					Thread.setDefaultUncaughtExceptionHandler(new MyExceptionCatcher());
					synchronized (this) {
						actionDone = false;
					}
					addFilter();
					utils.log("entering main loop");
					while (info.isOnPower && !info.isFull && !thTerminate) {
						try {
							Thread.sleep(60 * 1000);
						}
						catch (Exception ex) { }
						info.refresh();
					}
					utils.log("exiting main loop");
					delFilter();
					if (info.isOnPower && actionDone) {
						utils.log("settling down");
						try {
							Thread.sleep(5 * 1000);
						}
						catch (Exception ex) { }
					}
				}
			});
			th.setDaemon(true);
			th.start();
			if (settings.prefNotifications()) {
				fix.showNotification(getString(R.string.msg_start));
			}
		}
		catch (Exception ex) {
			Toast.makeText(this, String.format(getString(R.string.err_start), ex.getMessage()), Toast.LENGTH_LONG).show();
		}
	}
	
	protected void stop() {
		if (th == null) {
			return;
		}
		try {
			synchronized (this) {
				thTerminate = true;
			}
			th.interrupt();
			th.join();
			if (settings.prefNotifications()) {
				fix.showNotification(getString(R.string.msg_stop));
			}
		}
		catch (Exception ex) {
			Toast.makeText(this, String.format(getString(R.string.err_stop), ex.getMessage()), Toast.LENGTH_LONG).show();
		}
		th = null;
	}
}
