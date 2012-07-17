/***************************************************************************
 *   Copyright (C) 2009-2010 by mj                                         *
 *   fakeacc.mj@gmail.com  												   *
 *   Portions of this file Copyright (C) 2012 Jeremy Monin                 *
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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;


public class AnstopDbAdapter {

	/**
	 * Current database schema version.
	 * <UL>
	 * <LI> 2 - Version for Anstop v1.4 and earlier
	 * <LI> 3 - (2012-07-05) Add table {@link #TABLE_LAPS};
	 *            Add fields to {@link #DATABASE_TABLE}:
	 *            {@link #FIELD_TIMES_MODE}, {@link #FIELD_TIMES_START_SYSTIME},
	 *            {@link #FIELD_TIMES_STOP_SYSTIME}.
	 * <LI> 4 - (2012-07-17) Add {@link #DATABASE_TABLE} field {@link #FIELD_TIMES_ELAPSED};
	 *            add table {@link #TABLE_TEMP_LAPS}.
	 * </UL>
	 */
	private static final int DATABASE_VERSION = 4;

    // Reminder: Keep table names and field names here synchronized with onCreate and onUpgrade.

    private static final String DATABASE_NAME = "Anstop_db";

    /**
     * Main times table, one row per saved stopwatch time.
     * If the saved time has laps, they will be rows in {@link #TABLE_LAPS}
     * associated with one row in this table.
     */
    private static final String DATABASE_TABLE = "times";

    /**
     * Laps table, for lap information for one {@link #DATABASE_TABLE} row.
     * Same fields as {@link #TABLE_TEMP_LAPS}, plus {@link #FIELD_LAPS_TIMES_ROWID}.
     * @since v3
     */
    private static final String TABLE_LAPS = "laps";

    /**
     * temp_laps table, for lap information for the currently running timer.
     * Same fields as {@link #TABLE_LAPS}, omitting {@link #FIELD_LAPS_TIMES_ROWID}.
     * @since v4
     */
    private static final String TABLE_TEMP_LAPS = "temp_laps";

    /**
     * Title for one entry in {@link #DATABASE_TABLE}.
     */
    public static final String KEY_TITLE = "title";

    /**
     * Body (v2) or optional comment (v3+) for one entry in {@link #DATABASE_TABLE}.
     */
    public static final String KEY_BODY = "body";

    /**
     * Row ID field name in any table.
     */
    public static final String KEY_ROWID = "_id";

    /**
     * Mode field, same values as {@link Clock#getMode()}
     * @since v3
     */
    public static final String FIELD_TIMES_MODE = "mode";

    /**
     * Timer's starting system time (wall clock time), in milliseconds;
     * same as {@link Clock#getStartTimeActual()}
     * @since v3
     */
    public static final String FIELD_TIMES_START_SYSTIME = "start_systime";

    /**
     * System time when timing was stopped, in milliseconds.
     * Same as {@link Clock#getStartTimeActual()}.
     * @since v3
     */
    public static final String FIELD_TIMES_STOP_SYSTIME = "stop_systime";

    /**
     * Timer's elapsed (counting up) or remaining (counting down) time, in
     * milliseconds; same as {@link Clock#getCurrentValueMillis(StringBuilder, boolean)}.
     * @since v4
     */
    public static final String FIELD_TIMES_ELAPSED = "elapsed";

    /**
     * The {@link #DATABASE_TABLE} row id that a lap in {@link #TABLE_LAPS} belongs to.
     * @since v3
     */
    private static final String FIELD_LAPS_TIMES_ROWID = "times_id";

    /**
     * Lap's elapsed time, milliseconds for h:mm:ss:d, same format as
     * {@link Clock#getCurrentValueMillis(StringBuilder, boolean)}
     * or {@link Clock#lap_elapsed}.
     * @since v3
     */
    private static final String FIELD_LAPS_ELAPSED = "lap_elapsed";

    /**
     * Lap's time of day; {@link System#currentTimeMillis()} or {@link Clock#lap_systime}.
     * @since v3
     */
    private static final String FIELD_LAPS_SYSTIME = "lap_systime";

    /**
     * Optional lap comment.
     * @since v3
     */
    private static final String FIELD_LAPS_COMMENT = "lap_comment";

    private SQLiteDatabase mDb;
    private DataBaseHelper dbHelper;
    private Context mContext;

    /**
     * Date formatter for day of week + user's medium date format + hh:mm:ss;
     * used in {@link #getRowAndFormat(long)} for "started at:".
     */
    private StringBuffer fmt_dow_meddate_time;

    private static class DataBaseHelper extends SQLiteOpenHelper {

        DataBaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            db.execSQL("create table times (_id integer primary key autoincrement, "
                    + "title text not null, body text not null, "
                    + "mode int not null, start_systime int null, stop_systime int null, "
            		+ "elapsed int not null);" );

            // Use the same fields for laps and temp_laps, except times_id.

            db.execSQL("create table laps (_id integer primary key autoincrement, "
            		+ "times_id int not null, lap_elapsed int not null, "
            		+ "lap_systime int not null, lap_comment text null);" );
            db.execSQL("create index \"laps~t\" ON laps(times_id);" );

            db.execSQL("create table temp_laps (_id integer primary key autoincrement, "
            		+ "lap_elapsed int not null, "
            		+ "lap_systime int not null, lap_comment text null);" );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        	switch (oldVersion)
        	{
        	case 0:
        	case 1:
	            db.execSQL("DROP TABLE IF EXISTS times");
	            onCreate(db);
	            break;  // onCreate creates the db at latest schema version

        	case 2:  // 2 -> 3
                db.execSQL("create table laps (_id integer primary key autoincrement, "
                		+ "times_id int not null, lap_elapsed int not null, "
                		+ "lap_systime int not null, lap_comment text null);");
                db.execSQL("create index \"laps~t\" ON laps(times_id);");
                db.execSQL("alter table times add column mode int;");
                db.execSQL("alter table times add column start_systime int;");
                db.execSQL("alter table times add column stop_systime int;");
                // fall through

        	case 3:  // 3 -> 4
                db.execSQL("create table temp_laps (_id integer primary key autoincrement, "
                		+ "lap_elapsed int not null, "
                		+ "lap_systime int not null, lap_comment text null);" );
                db.execSQL("alter table times add column elapsed int;");
        	}
        }
    }
    
    /** Don't forget to call {@link #open()} before use, and {@link #close()} when done. */
    AnstopDbAdapter(Context context) {
    	this.mContext = context;
    }
    
    public AnstopDbAdapter open() throws SQLException {
    		dbHelper = new DataBaseHelper(mContext);
        	mDb = dbHelper.getWritableDatabase();
        	return this;
        }
    
    public void close() {
    		dbHelper.close();
    	}
    
    //
    // Methods for #DATABASE_TABLE
    //

    /**
     * Insert a new {@link #DATABASE_TABLE} record with the stopwatch current values.
     * If there are laps, add them afterwards by calling {@link #createNewLaps(long, int, long[], long[])}.
     * @param title  Title
     * @param comment   Comment, or null. In v2 this body text also included the start time and laps.
     * @param mode   Stopwatch mode used: {@link Anstop#STOP_LAP} or {@link Anstop#COUNTDOWN}
     * @param startTime  Start time (milliseconds), or -1L if never started.
     *    This same convention is returned by {@link Clock#getStartTimeActual()}.
     * @param stopTime   Stop time (milliseconds), or -1L for none
     * @param elapsed  Current elapsed time (counting up or down),
     *    from {@link Clock#getCurrentValueMillis(StringBuilder, boolean)}
     * @return  the new rowID, from {@link SQLiteDatabase#insert(String, String, ContentValues)}.
     */
    public long createNew
    	(final String title, final String comment,
		 final int mode, final long startTime, final long stopTime, final long elapsed)
    {
    	ContentValues cl = new ContentValues();
    	cl.put(KEY_TITLE, title);
    	if ((comment != null) && (comment.length() > 0))
    		cl.put(KEY_BODY, comment);
    	else
    		cl.put(KEY_BODY, "");
    	cl.put(FIELD_TIMES_MODE, mode);
    	if (startTime != -1L)
    		cl.put(FIELD_TIMES_START_SYSTIME, startTime);
    	if (stopTime != -1L)
    		cl.put(FIELD_TIMES_STOP_SYSTIME, stopTime);
    	cl.put(FIELD_TIMES_ELAPSED, elapsed);

    	return mDb.insert(DATABASE_TABLE, null, cl);
    }
    
    /**
     * Delete this entry from {@link #DATABASE_TABLE}, along with
     * any corresponding {@link #TABLE_LAPS}.
     * @param rowId  Row ID
     * @return  True if deleted, false if not found in {@link #DATABASE_TABLE}
     */
    public boolean delete(long rowId) {
    	final boolean didAny =
            (mDb.delete(DATABASE_TABLE, KEY_ROWID + "=" + rowId, null) > 0);
    	if (didAny)
    	{
    		mDb.delete(TABLE_LAPS, FIELD_LAPS_TIMES_ROWID + "=" + rowId, null);
    	}
    	return didAny;
    }

    /** Get basic info about all tracks: id, title, comment. */
    public Cursor fetchAll() {
    	
    	return mDb.query(DATABASE_TABLE, new String[] {KEY_ROWID, KEY_TITLE,
                    KEY_BODY}, null, null, null, null, KEY_ROWID);
    	
    }

    /**
     * Fetch all fields of a {@link #DATABASE_TABLE} record.
     * @param rowId  Record ID
     * @return The cursor, or null
     */
    public Cursor fetch(long rowId) throws SQLException {

        Cursor mCursor =
                mDb.query(true, DATABASE_TABLE,
                		new String[] {KEY_ROWID, KEY_TITLE, KEY_BODY, FIELD_TIMES_MODE,
                			FIELD_TIMES_START_SYSTIME, FIELD_TIMES_STOP_SYSTIME, FIELD_TIMES_ELAPSED},
                		KEY_ROWID + "=" + rowId, null,
                        null, null, null, null);
        if (mCursor != null) {
            mCursor.moveToFirst();
        }
        return mCursor;

    }

	/**
	 * Get this rowId's title and body from the database, including any lap data.
	 * If the record has a mode, start time, etc (v3 db schema or later),
	 * this data is retrieved and rendered into the returned body text,
	 * along with the comment field if any.
	 * Older records (v1 or v2) have these as text within body.
	 *<P>
	 * The same formatting is used in {@link Anstop#updateStartTimeCommentLapsView(boolean)}.
	 * If you change this method, change that one to match.
	 *
	 * @param rowId  The _id of the {@link #DATABASE_TABLE} record to retrieve
	 * @return String[] with [0]=title, [1]=body, or <tt>null</tt> if not found.
	 */
	public String[] getRowAndFormat(final long rowId) {
		String[] columns = null;
		Cursor time = null;
		try {
			time = fetch(rowId);
			columns = new String[2];
			columns[0] = time.getString(time.getColumnIndexOrThrow(KEY_TITLE));

			final int col_body = time.getColumnIndexOrThrow(KEY_BODY),
			          col_mode = time.getColumnIndex(FIELD_TIMES_MODE),
			          col_elapsed = time.getColumnIndex(FIELD_TIMES_ELAPSED),
			          col_starttime = time.getColumnIndex(FIELD_TIMES_START_SYSTIME);
			// col_stoptime = time.getColumnIndex(FIELD_TIMES_STOP_SYSTIME);
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

				Clock.LapFormatter lapf = new Clock.LapFormatter();

				// mode
				sb.append(mContext.getResources().getString(R.string.mode_was));
				sb.append(' ');
				if (Anstop.COUNTDOWN == time.getInt(col_mode))
					sb.append(mContext.getResources().getString(R.string.countdown));
				else
					sb.append(mContext.getResources().getString(R.string.stop));
				sb.append("\n\n");

				// duration
				if (! time.isNull(col_elapsed))
				{
					lapf.formatTimeLap(sb, false, -1, 0, 0, 0, 0, time.getLong(col_elapsed), 0, null);
					sb.append("\n\n");
				}

				// started at
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

				// comment
				final String comment = time.getString(col_body);
				if ((comment != null) && (comment.length() > 0))
				{
					if (sb.length() > 0)
						sb.append("\n\n");
					sb.append(comment);
				}

				// laps
				final int lapCount = countLaps(rowId);
				if (lapCount > 0)
				{
					if (sb.length() > 0)
						sb.append("\n\n");
					long[] lap_elapsed = new long[lapCount],
					       lap_systime = new long[lapCount];
					fetchAllLaps(rowId, lap_elapsed, lap_systime);
					final int fmtFlags = Anstop.readLapFormatPrefFlags
						(PreferenceManager.getDefaultSharedPreferences(mContext));
					if ((fmtFlags != 0) && (fmtFlags != Clock.LAP_FMT_FLAG_ELAPSED))
						lapf.setLapFormat
							(fmtFlags, android.text.format.DateFormat.getTimeFormat(mContext));
					lapf.formatTimeAllLaps(sb, lapCount + 1, lap_elapsed, lap_systime);
				}

				// All done.
				columns[1] = sb.toString();
			}

			time.close();

		} catch (SQLException e) {
			if (time != null)
				time.close();
		}

		return columns;
	}


    //
    // Methods for #TABLE_LAPS
    //

    /**
     * Insert the {@link #TABLE_LAPS} entries for all the active laps.
     * @param times_id  Row ID from {@link #createNew(String, String, int, long, long, long)}
     * @param laps  Number of laps to use from the arrays; {@link Clock#laps} - 1
     *     since that field is the <em>next</em> lap number
     * @param elapsed  Per-lap elapsed-time array, like {@link Clock#lap_elapsed}
     * @param systimes  Per-lap system-time (wall clock time) array, like {@link Clock#lap_systime}
     * @throws IllegalArgumentException  if array length is &lt; <tt>laps</tt>
     * @see #createNewLap(long, long, long)
     */
    public void createNewLaps
    	(final long times_id, final int laps, final long[] elapsed, final long[] systimes)
    	throws IllegalArgumentException
    {
    	if ((elapsed.length < laps) || (systimes.length < laps))
    		throw new IllegalArgumentException();
    	for (int i = 0; i < laps; ++i)
    		createNewLap(times_id, elapsed[i], systimes[i]);
    }

    /**
     * Insert a new {@link #TABLE_LAPS} entry for a new lap.
     * @param times_id  Row ID from {@link #createNew(String, String, int, long, long, long)},
     *    or 0 for temporary lap storage for the currently active timing.
     * @param elapsed  Per-lap elapsed-time
     * @param systime  Per-lap system-time
     * @see #createNewLaps(long, int, long[], long[])
     */
    public void createNewLap
    	(final long times_id, final long elapsed, final long systime)
    {
    	ContentValues cl = new ContentValues();
    	if (times_id != 0)
    		cl.put(FIELD_LAPS_TIMES_ROWID, times_id);
    	cl.put(FIELD_LAPS_ELAPSED, elapsed);
    	cl.put(FIELD_LAPS_SYSTIME, systime);

    	mDb.insert( ((times_id != 0) ? TABLE_LAPS : TABLE_TEMP_LAPS), null, cl);
    }

    /**
     * Delete any temporarily stored laps (table {@link #TABLE_TEMP_LAPS}).
     */
    public void deleteTemporaryLaps() {
    	mDb.delete(TABLE_TEMP_LAPS, null, null);    	
    }

    /**
     * Count the number of laps stored for a given {@link #DATABASE_TABLE} ID.
     * @param times_id  ID to fetch, or 0 for the currently active laps
     * @return  Number of laps, or 0 if not found
     * @see #fetchAllLaps(long, long[], long[])
     */
    public int countLaps(final long times_id) {
        Cursor mCursor =
            mDb.query( ((times_id != 0) ? TABLE_LAPS : TABLE_TEMP_LAPS),
        		new String[] { "count(" + KEY_ROWID + ')' },
        		((times_id != 0) ? FIELD_LAPS_TIMES_ROWID + "=" + times_id : null),
        		null, null, null, null);    	
    	if (mCursor == null)
    		return 0;
    	if (! mCursor.moveToFirst())
    	{
    		mCursor.close();
    		return 0;
    	}
    	final int lapCount = mCursor.getInt(0);
    	mCursor.close();
    	return lapCount;
    }

    /**
     * Fetch all laps for a given {@link #DATABASE_TABLE} ID.
     * To ensure <tt>elapsed[]</tt> and <tt>systimes[]</tt> are large
     * enough, call {@link #countLaps(long) countLaps(times_id)} first.
     * @param times_id  ID to fetch, or 0 for the currently active laps
     * @param elapsed  Array to hold each lap's elapsed
     * @param systimes  Array to hold each lap's systime
     * @return  The number of laps retrieved;
     *    if <tt>elapsed</tt> and <tt>systimes</tt> aren't large enough
     *    for all laps, this will be the size of the arrays.
     */
    public int fetchAllLaps(final long times_id, long[] elapsed, long[] systimes) {
    	
        Cursor mCursor =
            mDb.query( ((times_id != 0) ? TABLE_LAPS : TABLE_TEMP_LAPS),
        		new String[] { FIELD_LAPS_ELAPSED, FIELD_LAPS_SYSTIME},
        		((times_id != 0) ? FIELD_LAPS_TIMES_ROWID + "=" + times_id : null),
        		null, null, null, KEY_ROWID);
    	if (mCursor == null)
    		return 0;

    	int lapCount = 0;
        if (! mCursor.moveToFirst())
        {
        	mCursor.close();
        	return 0;
        }
        do {
        	if (lapCount >= elapsed.length)
        		break;  // array too small
        	elapsed[lapCount] = mCursor.getLong(0);
        	systimes[lapCount] = mCursor.getLong(1);
        	++lapCount;        	
        }
        while (mCursor.moveToNext());
        mCursor.close();

        return lapCount;
    }

}
