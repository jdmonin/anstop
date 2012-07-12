/***************************************************************************
 *   Copyright (C) 2009-2010 by mj                                         *
 *   fakeacc.mj@gmail.com                                                  *
 *   Portions of this file Copyright (C) 2010,2012 Jeremy Monin            *
 *     jeremy@nand.net                                                     *
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;

/**
 * Helper class to format and export time and lap data.
 * Does not hold any open db handles or cursors, so there is no Close method.
 */
public class ExportHelper {
	
	private FileOutputStream out;
	Context mContext;

	/**
	 * Date formatter for day of week + user's medium date format + hh:mm:ss;
	 * used in {@link #getRow(long)} for "started at:".
	 */
	private StringBuffer fmt_dow_meddate_time;

	public ExportHelper(Context context) {
		this.mContext = context;
	}

	/**
	 * Create and write a file on external storage (SD-Card). 
	 * @param title file's title; filename will be this + ".txt"
	 * @param body  Contents of file
	 * @return Success or failure
	 */
	public boolean write(String title, String body) {
		
		String storageState = Environment.getExternalStorageState();
		if (storageState.contains("mounted")) {
			File f = Environment.getExternalStorageDirectory();
			File j = new File(f, title + ".txt");
			
			try {
				out = new FileOutputStream(j);
			} catch (FileNotFoundException e) {
				return false;
			}
			
			try {
				out.write(body.getBytes());
			} catch (IOException e) {
				return false;
			}
			
			return true;
		}
		
		return false;
		
		
	}

	/**
	 * Export a saved record to disk.
	 * @param rowId The record's _id
	 * @return Success or failure
	 */
	public boolean write(long rowId) {
		String[] columns = getRow(rowId);
		if (columns == null)
			return false;
		boolean val = write(columns[0], columns[1]);
		return val;
		
	}

	/**
	 * Get this rowId's title and body from the database.
	 * If the record has a mode, start time, etc (v3 db schema or later),
	 * this data is retrieved and rendered into the returned body text,
	 * along with the comment field if any.
	 * Older records (v1 or v2) have these as text within body.
	 *<P>
	 * The same formatting is used in {@link Anstop#updateStartTimeCommentLapsView(boolean)}.
	 * If you change this method, change that one to match.
	 *
	 * @param rowId The _id of the record to retrieve
	 * @return String[] with [0]=title, [1]=body, or null if not found.
	 */
	public String[] getRow(long rowId) {
		AnstopDbAdapter dbHelper = null;
		String[] columns = null;
		try {
			dbHelper = new AnstopDbAdapter(mContext);
			dbHelper.open();
			
			Cursor time = dbHelper.fetch(rowId);
			columns = new String[2];
			columns[0] = time.getString(time.getColumnIndexOrThrow(AnstopDbAdapter.KEY_TITLE));

			final int col_body = time.getColumnIndexOrThrow(AnstopDbAdapter.KEY_BODY),
			          col_mode = time.getColumnIndex(AnstopDbAdapter.FIELD_TIMES_MODE),
			          col_starttime = time.getColumnIndex(AnstopDbAdapter.FIELD_TIMES_START_SYSTIME);
			// col_stoptime = time.getColumnIndex(AnstopDbAdapter.FIELD_TIMES_STOP_SYSTIME);
			if (time.isNull(col_mode))
			{
				// Simple: no mode
				columns[1] = time.getString(col_body);
			} else {
				// Mode, laps, start time are separate fields. Col_body contains the comment only.
				// Mode was: ___
				// duration
				// Started at: ___
				//\n
				// comment
				//\n
				// Laps:
				// lap info
				if (fmt_dow_meddate_time == null)
					fmt_dow_meddate_time = Anstop.buildDateFormatDOWmedium(mContext);
				StringBuilder sb = new StringBuilder();

				if (! time.isNull(col_starttime))
				{
					final long sttime = time.getLong(col_starttime);
					if (sttime != -1L)
					{
						sb.append(mContext.getResources().getText(R.string.started_at));
						sb.append(" ");
						sb.append(DateFormat.format(fmt_dow_meddate_time, sttime));
					}
				}

				final String comment = time.getString(col_body);
				if ((comment != null) && (comment.length() > 0))
				{
					if (sb.length() > 0)
						sb.append("\n\n");
					sb.append(comment);
				}

				final int lapCount = dbHelper.countLaps(rowId);
				if (lapCount > 0)
				{
					if (sb.length() > 0)
						sb.append("\n\n");
					long[] lap_elapsed = new long[lapCount],
					       lap_systime = new long[lapCount];
					dbHelper.fetchAllLaps(rowId, lap_elapsed, lap_systime);
					Clock.LapFormatter lapf = new Clock.LapFormatter();
					final int fmtFlags = Anstop.readLapFormatPrefFlags
						(PreferenceManager.getDefaultSharedPreferences(mContext));
					if ((fmtFlags != 0) && (fmtFlags != Clock.LAP_FMT_FLAG_ELAPSED))
						lapf.setLapFormat
							(fmtFlags, android.text.format.DateFormat.getTimeFormat(mContext));
					lapf.formatTimeAllLaps(sb, lapCount, lap_elapsed, lap_systime);
				}

				// All done.
				columns[1] = sb.toString();
			}
			
			dbHelper.close();
		} catch (SQLException e) {
			if (dbHelper != null)
				dbHelper.close();
		}
		return columns;
	}
	
	//TODO this does not work !!
	public boolean write(Cursor c) {
		c.moveToFirst();
		boolean val = true;
		
		for(int i = 0; i < c.getCount(); i++) {
			if(!write(c.getColumnName(i), c.getString(i)))
				val = false; //if one entry fails we will return false
							 //but we keep trying to write the other files
		}
		
		
			
		return val;
		
		
		
	}

}
