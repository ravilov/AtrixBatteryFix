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
import hr.ravilov.atrixbatteryfix.MyUtils;
import hr.ravilov.atrixbatteryfix.MyDialog;
import hr.ravilov.atrixbatteryfix.Settings;
import hr.ravilov.atrixbatteryfix.BatteryFix;
import hr.ravilov.atrixbatteryfix.BatteryInfo;
import hr.ravilov.atrixbatteryfix.R;

public class MainActivity extends Activity implements View.OnClickListener {
	private static final int REFRESH = 1000;	// 1 second
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
	private static boolean justStarted = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		MyUtils.init(this);
		Settings.init();
		BatteryInfo.init();
		BatteryFix.init(false);
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
		if (Settings.showAbout) {
			showAboutDialog(true);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
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
			Settings.load();
			BatteryInfo.refresh();
			BatteryFix.checkPower(false);
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
		d.setContents(String.format(getText(R.string.text_about).toString(), MyUtils.getMyVersion()));
		if (firstTime) {
			final CheckBox show = new CheckBox(this);
			show.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
			show.setText(R.string.no_show_about);
			show.setChecked(true);
			show.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					Settings.load();
					Settings.showAbout = isChecked ? false : true;
					Settings.save();
				}
			});
			d.getBottomView().setVisibility(View.VISIBLE);
			((LinearLayout)d.getBottomView()).addView(show);
			d.setOnClose(new MyDialog.OnCloseListener() {
				@Override
				public void run() {
					Settings.load();
					Settings.showAbout = show.isChecked() ? false : true;
					Settings.save();
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
					d.setContents(String.format(getText(R.string.text_licence).toString(), MyUtils.getMyVersion()));
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
					forceCalibrate();
				}
				break;
			case R.id.buttonFix: {
					fixBattd();
				}
				break;
			case R.id.buttonCharging: {
					charging.setChecked(charging.isChecked() ? false : true);
					if (BatteryFix.canCharging()) {
						BatteryFix.setCharging(BatteryFix.getCharging() ? false : true);
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
					BatteryFix.reboot();
				}
			})
			.setNegativeButton(getText(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			})
			.setNeutralButton(getText(R.string.dialog_restart), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					BatteryFix.restartBattd();
				}
			})
			.create()
			.show()
		;
	}

	private void forceCalibrate() {
		if (BatteryFix.run()) {
			actionDialog(getText(R.string.msg_done_action).toString());
		}
	}

	protected void fixBattd() {
		if (BatteryFix.fix()) {
			actionDialog(getText(R.string.msg_fixed_action).toString());
		}
	}

	protected void updateBatteryInfo() {
		BatteryInfo.refresh();
		if (BatteryInfo.isOnAC) {
			battSource.setText(getText(R.string.batt_source_ac));
		} else if (BatteryInfo.isOnUSB) {
			battSource.setText(getText(R.string.batt_source_usb));
		} else {
			battSource.setText(getText(R.string.batt_source_battery));
		}
		if (BatteryInfo.isFull) {
			if (BatteryInfo.isOnPower) {
				battState.setText(getText(R.string.batt_state_full));
			} else {
				battState.setText(getText(R.string.batt_state_discharging));
			}
		} else if (BatteryInfo.isCharging) {
			battState.setText(getText(R.string.batt_state_charging));
		} else if (BatteryInfo.isDischarging) {
			battState.setText(getText(R.string.batt_state_discharging));
		} else {
			battState.setText(getText(R.string.batt_state_unknown));
		}
		if (BatteryInfo.battVoltage != null) {
			battVoltage.setText(String.valueOf(Math.round(Float.valueOf(BatteryInfo.battVoltage) / 1000)) + " mV");
		} else {
			battVoltage.setText("-");
		}
		if (BatteryInfo.battTemp != null) {
			battTemp.setText(String.valueOf(Float.valueOf(BatteryInfo.battTemp) / 10) + " °C");
		} else {
			battTemp.setText("-");
		}
		if (BatteryInfo.battActual != null) {
			battActual.setText(String.valueOf(Integer.valueOf(BatteryInfo.battActual)) + "%");
		} else {
			battActual.setText("-");
		}
		if (BatteryInfo.battShown != null) {
			battShown.setText(String.valueOf(Integer.valueOf(BatteryInfo.battShown)) + "%");
		} else {
			if (BatteryInfo.battActual != null) {
				battShown.setText(String.valueOf(Integer.valueOf(BatteryInfo.battActual)) + "%");
			} else {
				battShown.setText("-");
			}
		}
		if (BatteryFix.canCharging()) {
			charging.setEnabled(true);
			boolean ch = BatteryFix.getCharging();
			charging.setChecked(ch ? true : false);
		} else {
			charging.setEnabled(false);
			charging.setText(R.string.nocharging);
		}
	}
}
