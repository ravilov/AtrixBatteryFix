package hr.ravilov.atrixbatteryfix;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.widget.Toast;
import hr.ravilov.atrixbatteryfix.BatteryInfo;

public class MonitorService extends Service {
	private Thread th;
	private volatile boolean thTerminate;

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public void onStart(Intent i, int startId) {
		super.onStart(i, startId);
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
		try {
			BatteryInfo.init(this);
			thTerminate = false;
			if (th != null) {
				stop();
			}
			th = new Thread(new Runnable() {
				private BroadcastReceiver br;

				private void reboot() {
					try {
						if (BatteryFix.autoFix) {
							try {
								BatteryFix.recalibrate();
							}
							catch (Exception ex) { }
						}
						BatteryFix.reboot();
					}
					catch (Exception ex) {
						try {
							BatteryFix.showNotification(getText(R.string.msg_autoreboot).toString());
						}
						catch (Exception ex1) { }
						BatteryFix.reboot();
					}
				}

				private void addFilter() {
					IntentFilter f = new IntentFilter();
					f.addAction(Intent.ACTION_BATTERY_CHANGED);
					br = new BroadcastReceiver() {
						@Override
						public void onReceive(Context c, Intent i) {
							BatteryInfo.refresh(i);
							if (BatteryInfo.isOnPower && BatteryInfo.isFull) {
								reboot();
							}
						}
					};
					registerReceiver(br, f);
				}

				private void delFilter() {
					unregisterReceiver(br);
				}

				@Override
				public void run() {
					addFilter();
					while (BatteryInfo.isOnPower && !MonitorService.this.thTerminate) {
						try {
							Thread.sleep(60 * 1000);
						}
						catch (Exception ex) { }
					}
					delFilter();
				}
			});
			th.setDaemon(true);
			th.start();
			if (BatteryFix.showNotifications) {
				BatteryFix.showNotification(getString(R.string.msg_start));
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
			thTerminate = true;
			th.interrupt();
			th.join();
			if (BatteryFix.showNotifications) {
				BatteryFix.showNotification(getString(R.string.msg_stop));
			}
		}
		catch (Exception ex) {
			Toast.makeText(this, String.format(getString(R.string.err_stop), ex.getMessage()), Toast.LENGTH_LONG).show();
		}
		th = null;
	}
}
