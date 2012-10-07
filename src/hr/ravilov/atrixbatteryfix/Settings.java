package hr.ravilov.atrixbatteryfix;

import android.os.Bundle;
//import android.preference.Preference;
import android.preference.PreferenceActivity;

public class Settings extends PreferenceActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.prefs);
/*
		final Preference notifications = (Preference)findPreference("notifications");
		notifications.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				return true;
			}
		});
*/
	}
}
