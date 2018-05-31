/***************************************************************************
 *   Copyright (C) 2009-2010 by mj   									   *
 *   fakeacc.mj@gmail.com  												   *
 *   Portions of this file Copyright (C) 2012 Jeremy Monin                 *
 *    jeremy@nand.net                                                      *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the                         *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 ***************************************************************************/


package An.stop;


import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.widget.Toast;

public class SettingsActivity
	extends android.preference.PreferenceActivity
	implements OnSharedPreferenceChangeListener {
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       
        addPreferencesFromResource(R.xml.settings);
	}

	@Override
	public void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

	/**
	 * Ensure at least one lap display format checkbox is set.
	 * (lap_format_elapsed, lap_format_delta, lap_format_systime)
	 * Fires when the user clicks to set or clear a checkbox.
	 */
	public void onSharedPreferenceChanged
		(SharedPreferences settings, final String key) {

		if (! key.startsWith("lap_format_"))
			return;

    	// read all 3 of the settings; default is LAP_FMT_FLAG_ELAPSED only
    	final boolean lapFmtElapsed = settings.getBoolean("lap_format_elapsed", true),
    	              lapFmtDelta   = settings.getBoolean("lap_format_delta", false),
    	              lapFmtSystime = settings.getBoolean("lap_format_systime", false);
    	if (lapFmtElapsed || lapFmtDelta || lapFmtSystime)
    		return;

    	Preference elapsed = findPreference("lap_format_elapsed");
    	if ((elapsed != null) && (elapsed instanceof CheckBoxPreference))
    		((CheckBoxPreference) elapsed).setChecked(true);
    	else
    		getPreferenceScreen().getEditor().putBoolean("lap_format_elapsed", true);

    	Toast.makeText(this, R.string.lap_display_format_must_select_one, Toast.LENGTH_SHORT).show();
	}

}