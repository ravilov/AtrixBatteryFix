package hr.ravilov.atrixbatteryfix;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Html;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity implements View.OnClickListener {
	private static final int REFRESH = 500;	// 0.5 second
	private Button force;
	private Button fix;
	private ToggleButton charging;
	private TextView battSource;
	private TextView battState;
	private TextView battVoltage;
	private TextView battTemp;
	private TextView battActual;
	private TextView battShown;
	private Thread updater;
	private volatile boolean updTerminate;
	private boolean justStarted = false;
	private MyUtils utils;
	private BatteryFix bfix;
	private BatteryInfo info;
	private Settings settings;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Thread.setDefaultUncaughtExceptionHandler(new MyExceptionCatcher());
		setContentView(R.layout.main);
		utils = new MyUtils(this);
		settings = (new Settings()).init(utils);
		info = new BatteryInfo(utils);
		bfix = new BatteryFix(utils, settings, info, false);
		bfix.setupChargingForUI();
		justStarted = true;
		force = (Button)findViewById(R.id.buttonForce);
		fix = (Button)findViewById(R.id.buttonFix);
		charging = (ToggleButton)findViewById(R.id.buttonCharging);
		battState = (TextView)findViewById(R.id.batt_state);
		battSource = (TextView)findViewById(R.id.batt_source);
		battVoltage = (TextView)findViewById(R.id.batt_voltage);
		battTemp = (TextView)findViewById(R.id.batt_temp);
		battShown = (TextView)findViewById(R.id.batt_shown);
		battActual = (TextView)findViewById(R.id.batt_actual);
		force.setOnClickListener(this);
		fix.setOnClickListener(this);
		charging.setOnClickListener(this);
		charging.setEnabled(false);
		updTerminate = false;
		updater = new Thread(new Runnable() {
			@Override
			public void run() {
				while (!updTerminate) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							MainActivity.this.updateBatteryInfo();
						}
					});
					try {
						Thread.sleep(REFRESH);
					}
					catch (Exception ex) { }
				}
			}
		});
		updater.setDaemon(false);
		updater.start();
		if (settings.prefAbout()) {
			showAboutDialog(true);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		bfix.cleanupCharging();
		try {
			if (updater != null) {
				synchronized (this) {
					updTerminate = true;
				}
				updater.interrupt();
				updater.join();
			}
		}
		catch (Exception ex) { }
	}

	@Override
	public void onResume() {
		super.onResume();
		if (!justStarted) {
			boolean oldUsb = settings.prefNoUsbCharging();
			Settings.PrefList p = settings.backup();
			settings.load();
			if (!settings.equals(p, settings.backup())) {
				info.refresh();
				bfix.checkPower();
				if (oldUsb && !settings.prefNoUsbCharging()) {
					bfix.setUsbCharging(true);
				}
			}
		}
		justStarted = false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}

	public void onConfigurationChanged(Configuration cfg) {
		super.onConfigurationChanged(cfg);
	}

	private void showAboutDialog(boolean firstTime) {
		MyDialog d = new MyDialog(this);
		d.setTitle(getText(R.string.menu_about).toString());
		d.setContents(String.format(getText(R.string.text_about).toString(), utils.getMyVersion()));
		if (firstTime) {
			final CheckBox show = new CheckBox(this);
			show.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
			show.setText(R.string.no_show_about);
			show.setChecked(true);
			show.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					settings.load();
					settings.prefAbout(isChecked ? false : true);
					settings.save();
				}
			});
			d.getBottomView().setVisibility(View.VISIBLE);
			((LinearLayout)d.getBottomView()).addView(show);
			d.setOnClose(new MyDialog.OnCloseListener() {
				@Override
				public void run() {
					settings.load();
					settings.prefAbout(show.isChecked() ? false : true);
					settings.save();
				}
			});
		}
		d.show();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_exit: {
					this.finish();
				}
				break;
			case R.id.menu_licence: {
					MyDialog d = new MyDialog(this);
					d.setTitle(getText(R.string.menu_licence).toString());
					d.setContents(String.format(getText(R.string.text_licence).toString(), utils.getMyVersion()));
					d.show();
				}
				break;
			case R.id.menu_settings: {
					startActivity(new Intent(this, Settings.class));
				}
				break;
			case R.id.menu_about: {
					showAboutDialog(false);
				}
				break;
		}
		return false;
	}

	@Override
	public void onClick(View src) {
		switch (src.getId()) {
			case R.id.buttonForce: {
					fixBattery();
				}
				break;
			case R.id.buttonFix: {
					fixBattd();
				}
				break;
			case R.id.buttonCharging: {
					charging.setChecked(charging.isChecked() ? false : true);
					try {
						if (bfix.canCharging()) {
							bfix.setCharging(bfix.getCharging() ? false : true);
						}
					}
					catch (Exception ex) {
						bfix.showError(R.string.err_charging, ex);
					}
				}
				break;
		}
	}

	private void actionDialog(String text) {
		new AlertDialog.Builder(this)
			.setTitle(getText(R.string.app_name))
			.setMessage(Html.fromHtml(text))
			.setCancelable(true)
			.setPositiveButton(getText(R.string.dialog_reboot), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss();
					bfix.reboot();
				}
			})
			.setNeutralButton(getText(R.string.dialog_restart), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					bfix.restartBattd();
				}
			})
			.setNegativeButton(getText(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			})
			.create()
			.show()
		;
	}

	private void fixBattery() {
		if (bfix.run()) {
			actionDialog(getText(R.string.msg_done_action).toString());
		}
	}

	protected void fixBattd() {
		if (bfix.fix()) {
			actionDialog(getText(R.string.msg_fixed_action).toString());
		}
	}

	protected void updateBatteryInfo() {
		info.refresh();
		if (info.isOnAC) {
			battSource.setText(getText(R.string.batt_source_ac));
		} else if (info.isOnUSB) {
			battSource.setText(getText(R.string.batt_source_usb));
		} else {
			battSource.setText(getText(R.string.batt_source_battery));
		}
		if (info.isFull) {
			if (info.isOnPower) {
				battState.setText(getText(R.string.batt_state_full));
			} else {
				battState.setText(getText(R.string.batt_state_discharging));
			}
		} else if (info.isCharging) {
			battState.setText(getText(R.string.batt_state_charging));
		} else if (info.isDischarging) {
			battState.setText(getText(R.string.batt_state_discharging));
		} else {
			battState.setText(getText(R.string.batt_state_unknown));
		}
		if (info.battVoltage != null) {
			battVoltage.setText(String.valueOf(Math.round(Float.valueOf(info.battVoltage) / 1000)) + " mV");
		} else {
			battVoltage.setText("-");
		}
		if (info.battTemp != null) {
			battTemp.setText(String.valueOf(Float.valueOf(info.battTemp) / 10) + " °C");
		} else {
			battTemp.setText("-");
		}
		if (info.battActual != null) {
			battActual.setText(String.valueOf(Integer.valueOf(info.battActual)) + "%");
		} else {
			battActual.setText("-");
		}
		if (info.battShown != null) {
			battShown.setText(String.valueOf(Integer.valueOf(info.battShown)) + "%");
		} else {
			if (info.battActual != null) {
				battShown.setText(String.valueOf(Integer.valueOf(info.battActual)) + "%");
			} else {
				battShown.setText("-");
			}
		}
		boolean canCharging = false;
		boolean isCharging = false;
		try {
			canCharging = bfix.canCharging() ? true : false;
			isCharging = (canCharging && bfix.getCharging()) ? true : false;
		}
		catch (Exception ex) {
			canCharging = false;
			isCharging = false;
		}
		if (canCharging) {
			charging.setEnabled(true);
			charging.setChecked(isCharging ? true : false);
		} else {
			charging.setEnabled(false);
			charging.setText(R.string.nocharging);
		}
	}
}
