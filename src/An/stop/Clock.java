/***************************************************************************
 *   Copyright (C) 2009-2011 by mj   									   *
 *   fakeacc.mj@gmail.com  												   *
 *   Portions of this file Copyright (C) 2010-2012 Jeremy Monin            *
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
import android.os.Bundle;

/**
 * Timer object and thread.
 *<P>
 * Has two modes ({@link AnstopActivity#STOP_LAP} and {@link AnstopActivity#COUNTDOWN}); clock's mode field is {@link #v}.
 * Has accessible fields for the current {@link #hour}, {@link #min}, {@link #sec}, {@link #dsec}.
 * Has a link to the {@link #parent} Anstop, and will sometimes read or set parent's text field contents.
 *<P>
 * Because of device power saving, there are methods to adjust the clock
 * when our app is paused/resumed: {@link #onAppPause()}, {@link #onAppResume()}.
 * Otherwise the counting would become inaccurate.
 *<P>
 * Has three states:
 *<UL>
 * <LI> Reset: This is the initial state.
 *        In STOP mode, the hour, minute, second are 0.
 *        In COUNTDOWN mode they're (h, m, s), copied from spinners when
 *        the user hits the "refresh" button.
 * <LI> Started: The clock is running.
 * <LI> Stopped: The clock is not currently running, but it's changed from
 *        the initial values.  That is, the clock is paused, and the user
 *        can start it to continue counting.
 *</UL>
 * You can examine the current state by reading {@link #isStarted} and {@link #wasStarted}.
 * To reset the clock again, and/or change the mode, call {@link #reset(int, int, int, int)}.
 *<P>
 * When running, a thread either counts up ({@link #threadS}) or down ({@link #threadC}),
 * firing every 100ms.  The {@link #parent}'s hour:minute:second.dsec displays are updated
 * through {@link #hourh} and the rest of the handlers here.
 *<P>
 * To lap, call {@link #lap(StringBuffer)}.  Note that persisting the lap data arrays
 * at Activity.onStop must be done in {@link AnstopActivity}, not here.
 * {@link #fillSaveState(Bundle)} stores the lap data arrays, but there's no corresponding
 * method to save long arrays to {@link SharedPreferences}.
 *<P>
 * Lap display formatting is done through flags such as {@link #LAP_FMT_FLAG_DELTA}
 * and the nested class {@link Clock.LapFormatter}.
 */
public class Clock {
	
}
