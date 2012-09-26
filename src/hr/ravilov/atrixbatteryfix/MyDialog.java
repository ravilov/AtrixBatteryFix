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
	static abstract class OnCloseListener implements Runnable {
		protected MyDialog dlg;
	}

	final static String btn_text = "OK";
	private Activity activity;
	private String title;
	private String contents;
	private View v;
	private View bottom;
	private OnCloseListener onClose;

	public MyDialog(Activity a) {
		activity = a;
		LayoutInflater li = (LayoutInflater)activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		v = li.inflate(R.layout.dialog, (ViewGroup)activity.findViewById(R.id.dialog_root));
		bottom = v.findViewById(R.id.dialog_bottom);
		bottom.setVisibility(View.GONE);
		onClose = null;
	}

	protected void runOnClose() {
		if (onClose == null) {
			return;
		}
		onClose.dlg = this;
		onClose.run();
	}

	public MyDialog setTitle(String t) {
		title = t;
		return this;
	}

	public MyDialog setContents(String c) {
		contents = c;
		return this;
	}

	public MyDialog setOnClose(OnCloseListener r) {
		onClose = r;
		return this;
	}

	public View getBottomView() {
		return bottom;
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
				runOnClose();
			}
		});
		builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				runOnClose();
			}
		});
		builder.create().show();
	}
}
