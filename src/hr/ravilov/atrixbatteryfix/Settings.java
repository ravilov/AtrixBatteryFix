package hr.ravilov.atrixbatteryfix;

import java.util.HashMap;
import java.util.Map;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import hr.ravilov.atrixbatteryfix.BatteryFix;

public class Settings extends PreferenceActivity {
	static public final String
		PREF_ABOUT = "about",
		PREF_NOTIFICATIONS = "notifications",
		PREF_BATTSTATS = "battstats",
		PREF_AUTOFIX = "enabled",
		PREF_AUTOACTION = "autoaction",
		PREF_NOUSBCHARGING = "nousbcharging"
	;
	static enum AutoAction {
		NONE,
		REBOOT,
		RESTART
	};

	public static boolean autoFix;
	public static boolean showNotifications;
	public static boolean battstats;
	public static boolean autoActionEnabled;
	public static AutoAction autoAction;
	public static boolean showAbout;
	public static boolean noUsbCharging;
	private static SharedPreferences prefs;
	protected CheckBoxPreference nousbcharging;
	protected ListPreference autoaction;

	static class cvt {
		static private boolean initted = false;
		static private HashMap<AutoAction, String> aaMap = new HashMap<AutoAction, String>();
		static private HashMap<String, String> eMap = new HashMap<String, String>();
		static {
			aaMap.clear();
			aaMap.put(AutoAction.NONE, "none");
			aaMap.put(AutoAction.REBOOT, "reboot");
			aaMap.put(AutoAction.RESTART, "restart");
		}

		public static void init() {
			if (initted) {
				return;
			}
			eMap.clear();
			String[] entries = MyUtils.getContext().getResources().getStringArray(R.array.pref_autoaction_values);
			String[] values = MyUtils.getContext().getResources().getStringArray(R.array.pref_autoaction_entries);
			for (int i = 0; i < entries.length; i++) {
				eMap.put(entries[i], values[i]);
			}
			initted = true;
		}

		public static AutoAction getAutoAction(String e) {
			for (AutoAction aa : aaMap.keySet()) {
				String ee = aaMap.get(aa);
				if (ee.equals(e)) {
					return aa;
				}
			}
			return null;
		}

		public static String getEntry(AutoAction aa) {
			return aaMap.get(aa);
		}

		public static String getDesc(String e) {
			return eMap.get(e);
		}

		public static String getDesc(AutoAction aa) {
			return eMap.get(getEntry(aa));
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MyUtils.init(this);
		addPreferencesFromResource(R.xml.prefs);
		nousbcharging = (CheckBoxPreference)findPreference("nousbcharging");
		nousbcharging.setEnabled(BatteryFix.canCharging() ? true : false);
		autoaction = (ListPreference)findPreference("autoaction");
		aaSetSummary();
		autoaction.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference pref, Object value) {
				aaSetSummary((String)value);
				return true;
			}
		});
	}

	protected void aaSetSummary(String value) {
		cvt.init();
		autoaction.setSummary(cvt.getDesc(value));
	}

	protected void aaSetSummary() {
		aaSetSummary(autoaction.getValue());
	}

	public static void init() {
		prefs = PreferenceManager.getDefaultSharedPreferences(MyUtils.getContext());
		load();
	}

	public static boolean hasOldProps() {
		final String[] list = {
			PREF_ABOUT,
			PREF_NOTIFICATIONS,
			PREF_BATTSTATS,
			PREF_AUTOFIX,
			PREF_AUTOACTION,
			PREF_NOUSBCHARGING,
		};
		Map<String, ?> entries = prefs.getAll();
		for (String key : entries.keySet()) {
			boolean found = false;
			for (int i = 0; !found && i < list.length; i++) {
				if (key.equals(list[i])) {
					continue;
				}
				found = true;
			}
			if (!found) {
				return true;
			}
		}
		return false;
	}

	public static void upgradeProps() {
		try {
			if (prefs.contains("showNotifications")) {
				showNotifications = prefs.getBoolean("showNotifications", showNotifications);
			}
			// TODO

		}
		catch (Exception ex) { }
	}

	public static void load() {
		cvt.init();
		try {
			autoFix = prefs.getBoolean(PREF_AUTOFIX, false);
			showNotifications = prefs.getBoolean(PREF_NOTIFICATIONS, true);
			autoAction = cvt.getAutoAction(prefs.getString(PREF_AUTOACTION, cvt.getEntry(AutoAction.NONE)));
			battstats = prefs.getBoolean(PREF_BATTSTATS, false);
			noUsbCharging = prefs.getBoolean(PREF_NOUSBCHARGING, false);
			showAbout = prefs.getBoolean(PREF_ABOUT, true);
		}
		catch (Exception ex) {
			// sane defaults
			autoFix = false;
			showNotifications = true;
			autoAction = AutoAction.NONE;
			battstats = false;
			noUsbCharging = false;
			showAbout = true;
		}
		if (hasOldProps()) {
			upgradeProps();
		}
	}

	public static void save() {
		cvt.init();
		SharedPreferences.Editor editor = prefs.edit();
		editor.clear();
		editor.putBoolean(PREF_AUTOFIX, autoFix);
		editor.putBoolean(PREF_NOTIFICATIONS, showNotifications);
		editor.putString(PREF_AUTOACTION, cvt.getEntry(autoAction));
		editor.putBoolean(PREF_BATTSTATS, battstats);
		editor.putBoolean(PREF_NOUSBCHARGING, noUsbCharging);
		editor.putBoolean(PREF_ABOUT, showAbout);
		editor.commit();
	}
}
