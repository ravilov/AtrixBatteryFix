package hr.ravilov.atrixbatteryfix;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class MyDialog {
	final static String btn_text = "OK";
	Activity activity;
	String title;
	String contents;
	View v;

	public MyDialog(Activity a) {
		activity = a;
		LayoutInflater li = (LayoutInflater)activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		v = li.inflate(R.layout.dialog, (ViewGroup)activity.findViewById(R.id.dialog_root));
	}

	public MyDialog setTitle(String t) {
		title = t;
		return this;
	}

	public MyDialog setContents(String c) {
		contents = c;
		return this;
	}

	public void show() {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setView(v);
		builder.setCancelable(true);
		builder.setTitle((CharSequence)title);
		TextView text = (TextView)v.findViewById(R.id.dialog_text);
		text.setText(Html.fromHtml(contents));
		text.setMovementMethod(LinkMovementMethod.getInstance());
		builder.setPositiveButton(btn_text, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		builder.create().show();
	}
}
