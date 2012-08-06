	package An.stop.widgets;

import java.text.NumberFormat;

import An.stop.R;
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

public class CountdownPicker extends LinearLayout implements OnClickListener {
	
	private static String TAG = "CountdownPicker";
	
	private Button addButton;
	private Button minusButton;
	private EditText numberEditText;
	private int value = 0;
	private NumberFormat nf;
	private int maximumValue = 60;

	public CountdownPicker(Context context, AttributeSet attrs) {
		super(context, attrs);
		LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.countdown_picker, this, true);
        
        nf = NumberFormat.getInstance();
        nf.setMinimumIntegerDigits(2);
        
        addButton = (Button) findViewById(R.id.add_button);
        minusButton = (Button) findViewById(R.id.minus_button);
        numberEditText = (EditText) findViewById(R.id.countdown_edittext);
        
        addButton.setOnClickListener(this);
        minusButton.setOnClickListener(this);
        
        numberEditText.setKeyListener(new DigitsKeyListener() {

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
	}

	public int getMaximumValue() {
		return maximumValue;
	}

	public void setMaximumValue(int maximumValue) {
		this.maximumValue = maximumValue;
	}

}
