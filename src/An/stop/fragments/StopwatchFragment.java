/***************************************************************************
 *   Copyright (C) 2009-2012 by mj <fakeacc.mj@gmail.com>, 				   *
 *   							Jeremy Monin <jeremy@nand.net>             *
 *                                                          			   *
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

package An.stop.fragments;

import An.stop.Clock;
import An.stop.R;

/**
 * Fragment for the stopwatch mode. Uses the stopwatch layout.
 * @see R#layout#stopwatch
 * @see Clock#MODE_STOPWATCH
 */
public class StopwatchFragment extends ClockFragment {

	public StopwatchFragment() {
		super(R.layout.stopwatch, Clock.MODE_STOPWATCH);
	}
	
	@Override
	protected void reset() {
		clock.reset();
		setDeciSeconds(0);
		setSeconds(0);
		setMinutes(0);
		setHours(0);
	}
}
