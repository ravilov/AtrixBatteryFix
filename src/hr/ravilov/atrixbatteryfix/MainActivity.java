package hr.ravilov.atrixbatteryfix;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import hr.ravilov.atrixbatteryfix.MyUtils;
import hr.ravilov.atrixbatteryfix.MyDialog;
import hr.ravilov.atrixbatteryfix.BatteryFix;
import hr.ravilov.atrixbatteryfix.BatteryInfo;
import hr.ravilov.atrixbatteryfix.R;

public class MainActivity extends Activity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
	private static final int REFRESH = 1000;	// 1 second
	private CheckBox autofix;
	private CheckBox notifications;
	private CheckBox autoreboot;
	private Button force;
	private Button fix;
	private TextView battSource;
	private TextView battState;
	private TextView battVoltage;
	private TextView battTemp;
	private TextView battActual;
	private TextView battShown;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		BatteryFix.init(this, false);
		BatteryInfo.init(this);
		autofix = (CheckBox)findViewById(R.id.autoFix);
		notifications = (CheckBox)findViewById(R.id.notifications);
		autoreboot = (CheckBox)findViewById(R.id.autoReboot);
		force = (Button)findViewById(R.id.buttonForce);
		fix = (Button)findViewById(R.id.buttonFix);
		battState = (TextView)findViewById(R.id.batt_state);
		battSource = (TextView)findViewById(R.id.batt_source);
		battVoltage = (TextView)findViewById(R.id.batt_voltage);
		battTemp = (TextView)findViewById(R.id.batt_temp);
		battShown = (TextView)findViewById(R.id.batt_shown);
		battActual = (TextView)findViewById(R.id.batt_actual);
		getPrefs();
		autofix.setOnCheckedChangeListener(this);
		notifications.setOnCheckedChangeListener(this);
		autoreboot.setOnCheckedChangeListener(this);
		force.setOnClickListener(this);
		fix.setOnClickListener(this);
		Thread th = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
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
		th.setDaemon(false);
		th.start();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}

	private void getPrefs() {
		autofix.setChecked(BatteryFix.autoFix ? true : false);
		notifications.setChecked(BatteryFix.showNotifications ? true : false);
		autoreboot.setChecked(BatteryFix.autoReboot ? true : false);
	}

	private void setPrefs() {
		BatteryFix.autoFix = autofix.isChecked() ? true : false;
		BatteryFix.showNotifications = notifications.isChecked() ? true : false;
		BatteryFix.autoReboot = autoreboot.isChecked() ? true : false;
	}

	private void savePrefs() {
		setPrefs();
		BatteryFix.savePrefs();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_exit: {
					savePrefs();
					this.finish();
				}
				break;
			case R.id.menu_licence: {
					MyDialog d = new MyDialog(this);
					d.setTitle(getText(R.string.menu_licence).toString());
					d.setContents(String.format(getText(R.string.text_licence).toString(), MyUtils.getMyVersion(this)));
					d.show();
				}
				break;
			case R.id.menu_about: {
					MyDialog d = new MyDialog(this);
					d.setTitle(getText(R.string.menu_about).toString());
					d.setContents(String.format(getText(R.string.text_about).toString(), MyUtils.getMyVersion(this)));
					d.show();
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
		}
	}

	@Override
	public void onCheckedChanged(CompoundButton btn, boolean value) {
		savePrefs();
		BatteryInfo.refresh();
		if (BatteryInfo.isOnPower && !BatteryInfo.isFull && BatteryFix.autoReboot) {
			BatteryFix.monCondStart();
		} else {
			BatteryFix.monCondStop();
		}
	}

	private void forceCalibrate() {
		savePrefs();
		BatteryFix.init(this, false);
		if (BatteryFix.run()) {
			new AlertDialog.Builder(this)
				.setTitle(getText(R.string.app_name))
				.setMessage(getText(R.string.msg_done_reboot))
				.setCancelable(true)
				.setPositiveButton(getText(R.string.reboot_yes), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						dialog.dismiss();
						BatteryFix.reboot();
					}
				})
				.setNegativeButton(getText(R.string.reboot_no), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				})
				.create()
				.show()
			;
		}
	}

	protected void fixBattd() {
		savePrefs();
		BatteryFix.init(this, false);
		BatteryFix.fix();
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
	}
}
