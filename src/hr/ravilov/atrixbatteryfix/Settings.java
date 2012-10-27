package hr.ravilov.atrixbatteryfix;

import java.util.HashMap;
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
		PREF_AUTOFIX = "autofix",
		PREF_AUTOACTION = "autoaction",
		PREF_NOUSBCHARGING = "nousbcharging"
	;

	public static enum AutoAction {
		NONE,
		REBOOT,
		RESTART
	};

	private static class Pref<V> {
		public V value = null;

		@SuppressWarnings("unchecked")
		public void set(Object x) {
			if (x == null) {
				return;
			}
			try {
				value = (V)x;
			}
			catch (Exception ex) { }
		}
	}

	@SuppressWarnings("serial")
	public static class PrefList extends HashMap<String, Object> { }

	private static class Prefs {
		private HashMap<String, Pref<?>> list;

		public Prefs() {
			list = new HashMap<String, Pref<?>>();
		}

		public void add(String key, Pref<?> pref) {
			list.put(key, pref);
		}

		public Pref<?> get(String key) {
			return list.get(key);
		}

		public boolean exists(String key) {
			return list.containsKey(key) ? true : false;
		}

		public String[] list() {
			String[] tmp = {};
			return list.keySet().toArray(tmp);
		}

		public PrefList getAll() {
			PrefList ret = new PrefList();
			for (String key : list()) {
				ret.put(key, list.get(key).value);
			}
			return ret;
		}
	}

	public static final Prefs prefs = new Prefs();
	static {
		prefs.add(PREF_ABOUT, new Pref<Boolean>());
		prefs.add(PREF_NOTIFICATIONS, new Pref<Boolean>());
		prefs.add(PREF_BATTSTATS, new Pref<Boolean>());
		prefs.add(PREF_AUTOFIX, new Pref<Boolean>());
		prefs.add(PREF_AUTOACTION, new Pref<AutoAction>());
		prefs.add(PREF_NOUSBCHARGING, new Pref<Boolean>());
	}

	protected static SharedPreferences pref;
	protected CheckBoxPreference nousbcharging;
	protected ListPreference autoaction;

	private static class cvt {
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
		pref = PreferenceManager.getDefaultSharedPreferences(MyUtils.getContext());
		load();
	}

	public static boolean hasOldProps() {
		for (String key : prefs.getAll().keySet()) {
			boolean found = false;
			for (String k2 : prefs.list()) {
				if (key.equals(k2)) {
					found = true;
				}
				if (found) {
					break;
				}
			}
			if (!found) {
				return true;
			}
		}
		return false;
	}

	public static void upgradeProps() {
		try {
			if (pref.contains("enabled")) {
				prefs.get(PREF_AUTOFIX).set(pref.getBoolean("enabled", ((Boolean)prefs.get(PREF_AUTOFIX).value).booleanValue()));
			}
			if (pref.contains("autofix_enabled")) {
				prefs.get(PREF_AUTOFIX).set(pref.getBoolean("autofix_enabled", ((Boolean)prefs.get(PREF_AUTOFIX).value).booleanValue()));
			}
			if (pref.contains("showNotifications")) {
				prefs.get(PREF_NOTIFICATIONS).set(pref.getBoolean("showNotifications", ((Boolean)prefs.get(PREF_NOTIFICATIONS).value).booleanValue()));
			}
			// TODO: more
		}
		catch (Exception ex) { }
		save();
	}

	public static void load() {
		// defaults
		prefs.get(PREF_AUTOFIX).set(false);
		prefs.get(PREF_NOTIFICATIONS).set(true);
		prefs.get(PREF_AUTOACTION).set(AutoAction.NONE);
		prefs.get(PREF_BATTSTATS).set(false);
		prefs.get(PREF_NOUSBCHARGING).set(false);
		prefs.get(PREF_ABOUT).set(true);
		cvt.init();
		try {
			prefs.get(PREF_AUTOFIX).set(pref.getBoolean(PREF_AUTOFIX, ((Boolean)prefs.get(PREF_AUTOFIX).value).booleanValue()));
			prefs.get(PREF_NOTIFICATIONS).set(pref.getBoolean(PREF_NOTIFICATIONS, ((Boolean)prefs.get(PREF_NOTIFICATIONS).value).booleanValue()));
			prefs.get(PREF_AUTOACTION).set(cvt.getAutoAction(pref.getString(PREF_AUTOACTION, cvt.getEntry((AutoAction)prefs.get(PREF_AUTOACTION).value))));
			prefs.get(PREF_BATTSTATS).set(pref.getBoolean(PREF_BATTSTATS, ((Boolean)prefs.get(PREF_BATTSTATS).value).booleanValue()));
			prefs.get(PREF_NOUSBCHARGING).set(pref.getBoolean(PREF_NOUSBCHARGING, ((Boolean)prefs.get(PREF_NOUSBCHARGING).value).booleanValue()));
			prefs.get(PREF_ABOUT).set(pref.getBoolean(PREF_ABOUT, ((Boolean)prefs.get(PREF_ABOUT).value).booleanValue()));
		}
		catch (Exception ex) { }
		if (hasOldProps()) {
			upgradeProps();
		}
	}

	public static void save() {
		cvt.init();
		SharedPreferences.Editor editor = pref.edit();
		editor.clear();
		editor.putBoolean(PREF_AUTOFIX, ((Boolean)prefs.get(PREF_AUTOFIX).value).booleanValue());
		editor.putBoolean(PREF_NOTIFICATIONS, ((Boolean)prefs.get(PREF_NOTIFICATIONS).value).booleanValue());
		editor.putString(PREF_AUTOACTION, cvt.getEntry((AutoAction)prefs.get(PREF_AUTOACTION).value));
		editor.putBoolean(PREF_BATTSTATS, ((Boolean)prefs.get(PREF_BATTSTATS).value).booleanValue());
		editor.putBoolean(PREF_NOUSBCHARGING, ((Boolean)prefs.get(PREF_NOUSBCHARGING).value).booleanValue());
		editor.putBoolean(PREF_ABOUT, ((Boolean)prefs.get(PREF_ABOUT).value).booleanValue());
		editor.commit();
	}

	public static PrefList backup() {
		return prefs.getAll();
	}

	public static void restore(PrefList p) {
		for (String key : p.keySet()) {
			if (!prefs.exists(key)) {
				continue;
			}
			prefs.get(key).set(p.get(key));
		}
	}

	public static boolean equals(PrefList p1, PrefList p2) {
		String[] tmp = {};
		String[] keys1 = p1.keySet().toArray(tmp);
		String[] keys2 = p2.keySet().toArray(tmp);
		if (keys1.length != keys2.length) {
			return false;
		}
		for (String k1 : keys1) {
			boolean found = false;
			for (String k2 : keys2) {
				if (k1.equals(k2)) {
					found = true;
				}
				if (found) {
					break;
				}
			}
			if (!found) {
				return false;
			}
			Object v1 = p1.get(k1);
			Object v2 = p2.get(k1);
			if ((v1 == null && v2 != null) || (v1 != null && v2 == null)) {
				return false;
			}
			if (!v1.equals(v2)) {
				return false;
			}
		}
		return true;
	}


	// shortcut methods

	public static boolean prefAutoFix(Boolean value) {
		prefs.get(PREF_AUTOFIX).set(value);
		return (Boolean)prefs.get(PREF_AUTOFIX).value;
	}

	public static boolean prefAutoFix() {
		return prefAutoFix(null);
	}

	public static boolean prefNotifications(Boolean value) {
		prefs.get(PREF_NOTIFICATIONS).set(value);
		return (Boolean)prefs.get(PREF_NOTIFICATIONS).value;
	}

	public static boolean prefNotifications() {
		return prefNotifications(null);
	}

	public static AutoAction prefAutoAction(AutoAction value) {
		prefs.get(PREF_AUTOACTION).set(value);
		return (AutoAction)prefs.get(PREF_AUTOACTION).value;
	}

	public static AutoAction prefAutoAction() {
		return prefAutoAction(null);
	}

	public static boolean prefBattStats(Boolean value) {
		prefs.get(PREF_BATTSTATS).set(value);
		return (Boolean)prefs.get(PREF_BATTSTATS).value;
	}

	public static boolean prefBattStats() {
		return prefBattStats(null);
	}

	public static boolean prefNoUsbCharging(Boolean value) {
		prefs.get(PREF_NOUSBCHARGING).set(value);
		return (Boolean)prefs.get(PREF_NOUSBCHARGING).value;
	}

	public static boolean prefNoUsbCharging() {
		return prefNoUsbCharging(null);
	}

	public static boolean prefAbout(Boolean value) {
		prefs.get(PREF_ABOUT).set(value);
		return (Boolean)prefs.get(PREF_ABOUT).value;
	}

	public static boolean prefAbout() {
		return prefAbout(null);
	}
}
