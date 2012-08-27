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

package An.stop.widgets;

import java.text.NumberFormat;

import An.stop.R;
import An.stop.util.Util;
import android.content.Context;
import android.text.Spanned;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * UI widget which lets the user pick some Integer number between 0 and
 * {@link #maximumValue}.
 */
public class TimePicker extends LinearLayout implements OnClickListener {
	
	private static String TAG = "CountdownPicker";
	
	private Button addButton;
	private Button minusButton;
	private EditText numberEditText;
	private int value = 0;
	private NumberFormat nf;
	private int maximumValue = 60;

	public TimePicker(Context context, AttributeSet attrs) {
		super(context, attrs);
		LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.time_picker, this, true);
        
        nf = Util.getTwoDigitFormat();
        
        addButton = (Button) findViewById(R.id.add_button);
        minusButton = (Button) findViewById(R.id.minus_button);
        numberEditText = (EditText) findViewById(R.id.countdown_edittext);
        
        addButton.setOnClickListener(this);
        minusButton.setOnClickListener(this);
        
        numberEditText.setKeyListener(new DigitsKeyListener() {
        	@Override
        	public CharSequence filter(CharSequence source, int start, int end,
        			Spanned dest, int dstart, int dend) {
        		Log.d(TAG, "filter ...");
        		// ask if normal DigitKeyListener would accept it
        		CharSequence cs = super.filter(source, start, end, dest, dstart, dend);
        		if(cs != null)
        			return cs;
        		
        		// if yes, check if in range
        		int input = Integer.parseInt(dest.toString() + source.toString());
        		if(input >= 0 && input <= maximumValue) {
        			value = input;
        			return null; // this is okay
        		}
        		else
        			return ""; // replace with nothing
        		
        	}
        });
        
        setNumberText();
	}

	public void onClick(View v) {
		switch(v.getId()) {
		case R.id.add_button:
			if(value < maximumValue) {
				value++;
				setNumberText();
			}
			break;
		case R.id.minus_button:
			if(value > 0) {
				value--;
				setNumberText();
			}
			break;
		}
	}
	
	private void setNumberText() {
        numberEditText.setText(nf.format(value));
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
		setNumberText();
	}

	public int getMaximumValue() {
		return maximumValue;
	}

	public void setMaximumValue(int maximumValue) {
		this.maximumValue = maximumValue;
	}

}
