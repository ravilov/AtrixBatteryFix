package hr.ravilov.atrixbatteryfix;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.PowerManager;
import android.util.Log;

public class MyUtils {
	static protected final String[] suCandidates = {
		"/system/xbin/su",
		"/system/sbin/su",
		"/system/bin/su",
		"/sbin/su",
		"/data/local/sbin/su",
		"/data/local/bin/su",
	};
	static protected final String[] shCandidates = {
		"/system/xbin/bash",
		"/system/bin/sh",
		"/bin/sh",
		"/sbin/sh",
	};
	static protected final String[] bbShCandidates = {
		"sh",
		"ash",
	};

	static public enum Debug {
		UNKNOWN,
		NO,
		YES
	};
	static public Debug debug = Debug.UNKNOWN;

	private Context context = null;
	private Method reboot = null;
	private String su = null;
	private String sh = null;
	private String bb = null;
	public String defaultTag = null;

	public MyUtils(Context ctx) {
		context = ctx;
		getDebug();
		shFind();
		busyboxFind();
		suFind();
		canSysReboot();
	}

	public Context getContext() {
		try {
			Context base = getBaseContext();
			Context c = base.getApplicationContext();
			return (c == null) ? base : c;
		}
		catch (Exception ex) { }
		return null;
	}

	public Context getBaseContext() {
		return context;
	}

	static public String getStackTrace(Exception ex) {
		ByteArrayOutputStream s = new ByteArrayOutputStream();
		PrintWriter pw = new PrintWriter(s);
		ex.printStackTrace(pw);
		pw.close();
		return s.toString();
	}

	static public final String getFullAction(String action) {
		return MyUtils.class.getPackage().getName() + "." + action;
	}

	public void broadcast(String action) {
		if (context == null) {
			return;
		}
		Intent i = new Intent(getFullAction(action));
		context.sendBroadcast(i);
	}

	public String getMyVersion() {
		try {
			PackageInfo pkg = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			return pkg.versionName;
		}
		catch (Exception ex) { }
		return null;
	}

	public String runShellLike(String shell, String dir, String... cmd) throws Exception {
		if (shell == null || shell.equals("")) {
			throw new Exception("shell not defined");
		}
		try {
			StringBuilder cmds = new StringBuilder();
			if (cmd.length > 0) {
				cmds.append(cmd[0]);
				for (int i = 1; i < cmd.length; i++) {
					cmds.append(" ; ");
					cmds.append(cmd[i]);
				}
			}
			log("<exec>", String.format("Running [%s] using [%s]", cmds.toString(), shell));
		}
		catch (Exception ex) { }
		ShellInterface sh = new ShellInterface(this, shell, dir);
		for (int i = 0; i < cmd.length; i++) {
			if (cmd[i] != null && !cmd[i].equals("")) {
				sh.sendCommand(cmd[i]);
			}
		}
		sh.finish();
		String res = "";
		while (sh.hasError()) {
			res += sh.getError(false);
		}
		while (sh.hasOutput()) {
			res += sh.getOutput(false);
		}
		sh.close();
		res = ShellInterface.trim(res);
		return res;
	}

	public String shRun(String dir, String... cmd) throws Exception {
		String sh = shFind();
		if (sh.equals("")) {
			throw new Exception("shell not found");
		}
		return runShellLike(sh, dir, cmd);
	}

	public String suRun(String dir, String... cmd) throws Exception {
		String su = suFind();
		if (su.equals("")) {
			throw new Exception("su binary not found");
		}
		return runShellLike(su, dir, cmd);
	}

	public String extractScript(int scriptId) throws Exception {
		String script = context.getResources().getResourceEntryName(scriptId);
		File f = context.getFileStreamPath(script);
		if (f.exists()) {
			return f.getAbsolutePath();
		}
		try {
			InputStream raw = context.getResources().openRawResource(scriptId);
			BufferedReader is = new BufferedReader(new InputStreamReader(raw, "UTF-8"));
			BufferedWriter os = new BufferedWriter(new OutputStreamWriter(context.openFileOutput(script, Context.MODE_PRIVATE), "UTF-8"));
			while (is.ready()) {
				String line = is.readLine();
				os.write(line + "\n");
			}
			is.close();
			os.close();
		}
		catch (Exception ex) {
			String msg = ex.getMessage();
			if (msg == null || msg.equals("")) {
				msg = "datadir not found or invalid, please reinstall app";
			}
			throw new Exception(msg);
		}
		return f.getAbsolutePath();
	}

	public String shRunScript(String dir, int scriptId) throws Exception {
		String script = extractScript(scriptId);
		if (script == null) {
			return null;
		}
		return shRun(dir, shFind() + " '" + script + "'");
	}

	public String suRunScript(String dir, int scriptId) throws Exception {
		String script = extractScript(scriptId);
		if (script == null) {
			return null;
		}
		return suRun(dir, shFind() + " '" + script + "'");
	}

	public boolean canSysReboot() {
		if (reboot == null) {
			Method m[] = PowerManager.class.getDeclaredMethods();
			for (int i = 0; reboot == null && i < m.length; i++) {
				if (m[i].getName().equals("reboot")) {
					reboot = m[i];
				}
			}
		}
		return (reboot == null) ? false : true;
	}

	public void rebootApi(String reason) throws Exception {
		if (!canSysReboot()) {
			throw new Exception("Reboot not supported");
		}
		PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
		reboot.invoke(pm, (Object)null);
	}

	public void rebootCommand(String reason) throws Exception {
		String cmd = "reboot";
		if (reason != null && !reason.equals("")) {
			cmd += " " + reason;
		}
		String ret = suRun(null, cmd);
		if (!ret.equals("")) {
			throw new Exception(ret);
		}
	}

	static public String pathFind(String cmd) {
		String path = System.getenv("PATH");
		if (path == null || path.equals("")) {
			return null;
		}
		String[] dirs = path.split(":");
		for (int i = 0; i < dirs.length; i++) {
			File f = new File(dirs[i]);
			if (!f.exists()) {
				continue;
			}
			if (!f.isDirectory()) {
				continue;
			}
			f = new File(f.getAbsoluteFile() + "/" + cmd);
			if (!f.exists()) {
				continue;
			}
			return f.getAbsolutePath();
		}
		return null;
	}

	public String busyboxFind() {
		if (bb != null) {
			return bb;
		}
		bb = pathFind("busybox");
		return bb;
	}

	public String shFind() {
		if (sh != null) {
			return sh;
		}
		String bb = busyboxFind();
		if (bb != null) {
			for (int i = 0; i < bbShCandidates.length; i++) {
				String bbsh = bb + " " + bbShCandidates[i];
				try {
					String res = runShellLike(bbsh, null);
					if (res != null && !res.equals("")) {
						throw new Exception(res);
					}
					sh = bbsh;
				}
				catch (Exception ex) { }
				if (sh != null) {
					break;
				}
			}
		}
		if (sh == null) {
			for (int i = 0; i < shCandidates.length; i++) {
				File f = new File(shCandidates[i]);
				if (f.exists()) {
					sh = f.getAbsolutePath();
				}
				if (sh != null) {
					break;
				}
			}
		}
		if (sh == null) {
			// default - system shell
			sh = pathFind("sh");
		}
		return sh;
	}

	public String suFind() {
		if (su != null) {
			return su;
		}
		String fnd = pathFind("su");
		if (fnd != null && !fnd.equals("")) {
			return fnd;
		}
		for (int i = 0; i < suCandidates.length; i++) {
			File f = new File(suCandidates[i]);
			if (f.exists()) {
				su = f.getAbsolutePath();
			}
			if (su != null) {
				break;
			}
		}
		if (su == null) {
			// default fallback
			su = "su";
		}
		return su;
	}

	protected void getDebug() {
		if (debug != Debug.UNKNOWN) {
			return;
		}
		if (context == null) {
			return;
		}
		Integer v = context.getResources().getInteger(R.integer.debug);
		debug = (v == 0) ? Debug.NO : Debug.YES;
	}

	public void log(String tag, String msg) {
		getDebug();
		if (debug != Debug.YES) {
			return;
		}
		Log.d(tag, msg);
	}

	public void log(String msg) {
		getDebug();
		if (debug != Debug.YES) {
			return;
		}
		if (defaultTag == null) {
			try {
				PackageInfo pkg = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
				String name = pkg.applicationInfo.name;
				if (name == null) {
					name = context.getText(pkg.applicationInfo.labelRes).toString();
				}
				defaultTag = String.format("<%s>", name);
			}
			catch (Exception ex) {
				defaultTag = "<applog>";
			}
		}
		log(defaultTag, msg);
	}
}
