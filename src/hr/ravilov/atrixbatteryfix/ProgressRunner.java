package hr.ravilov.atrixbatteryfix;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Looper;
import android.widget.Toast;

public abstract class ProgressRunner {
	public ProgressDialog pd = null;
	public Exception ex = null;
	private MyUtils utils;
	private String msg_progress;
	private String msg_success;
	private String msg_error;

	abstract public void onRun() throws Exception;

	public ProgressRunner(MyUtils u, String mProgress, String mSuccess, String mError) {
		utils = u;
		init(mProgress, mSuccess, mError);
	}

	public ProgressRunner(MyUtils u, int mProgress, int mSuccess, int mError) {
		utils = u;
		init(
			(mProgress > 0) ? utils.getContext().getText(mProgress).toString() : null,
			(mSuccess > 0) ? utils.getContext().getText(mSuccess).toString() : null,
			(mError > 0) ? utils.getContext().getText(mError).toString() : null
		);
	}

	private void init(String mProgress, String mSuccess, String mError) {
		msg_progress = mProgress;
		msg_success = mSuccess;
		msg_error = mError;
	}

	public void show() {
		if (pd != null) {
			return;
		}
		pd = ProgressDialog.show(utils.getBaseContext(), "", (msg_progress == null) ? "" : msg_progress, true, false);
		pd.setCancelable(false);
	}

	public void hide() {
		if (pd == null) {
			return;
		}
		pd.dismiss();
		pd = null;
	}

	public void success() {
		if (msg_success == null) {
			return;
		}
		Toast.makeText(utils.getBaseContext(), msg_success, Toast.LENGTH_SHORT).show();
	}

	public void error() {
		if (ex == null) {
			return;
		}
		if (msg_error == null) {
			return;
		}
		Toast.makeText(utils.getBaseContext(), String.format(msg_error, ex.getMessage()), Toast.LENGTH_LONG).show();
	}

	public void run() {
		final Activity act = (Activity)utils.getBaseContext();
		act.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				show();
			}
		});
		try {
			Thread th = new Thread(new Runnable() {
				@Override
				public void run() {
					Looper.prepare();
					ex = null;
					try {
						onRun();
					}
					catch (Exception ex2) {
						ex = ex2;
					}
					act.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							hide();
							if (ex == null) {
								success();
							} else {
								error();
							}
						}
					});
					Looper.loop();
					Looper.myLooper().quit();
				}
			});
			th.setDaemon(false);
			th.start();
		}
		catch (Exception ex2) {
			ex = ex2;
			hide();
			error();
		}
	}
}
