package hr.ravilov.atrixbatteryfix;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
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
	static private Method reboot = null;
	static private String su = null;

	static public final String
		PREF_AUTOFIX = "enabled",
		PREF_AUTOREBOOT = "autoReboot",
		PREF_NOTIFICATIONS = "notifications"
	;
	static protected final String[] suCandidates = {
		"/system/xbin/su",
		"/system/sbin/su",
		"/system/bin/su",
		"/sbin/su",
		"/data/local/bin/su",
	};

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

	static public void broadcast(Context ctx, String action) {
		if (ctx == null) {
			return;
		}
		Intent i = new Intent(getFullAction(action));
		ctx.sendBroadcast(i);
	}

	static public String getMyVersion(Context ctx) {
		try {
			PackageInfo pkg = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
			return pkg.versionName;
		}
		catch (Exception ex) { }
		return null;
	}

	static public String suFind() {
		String fnd = pathFind("su");
		if (fnd != null && !fnd.equals("")) {
			return fnd;
		}
		for (int i = 0; i < suCandidates.length; i++) {
			File f = new File(suCandidates[i]);
			if (f.exists()) {
				return suCandidates[i];
			}
		}
		// default fallback
		return "su";
	}

	static public String suRun(String dir, String... cmd) throws Exception {
		if (su == null) {
			su = suFind();
		}
		if (su.equals("")) {
			throw new Exception("su binary not found");
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
			Log.v("<suexec>", String.format("Executing [%s] using [%s]", cmds.toString(), su));
		}
		catch (Exception ex) { }
		Process p = (dir == null) ? Runtime.getRuntime().exec(su) : Runtime.getRuntime().exec(su, null, new File(dir));
		DataOutputStream os = new DataOutputStream(p.getOutputStream());
		DataInputStream is = new DataInputStream(p.getInputStream());
		DataInputStream es = new DataInputStream(p.getErrorStream());
		for (int i = 0; i < cmd.length; i++) {
			if (cmd[i] != null && !cmd[i].equals("")) {
				os.writeBytes(cmd[i] + "\n");
			}
		}
		os.flush();
		os.close();
		p.waitFor();
		String res = "";
		while (es.available() > 0) {
			res += es.readLine() + "\n";
		}
		while (is.available() > 0) {
			res += is.readLine() + "\n";
		}
		is.close();
		es.close();
		res = res.replaceAll("(^[\\s\\r\\n]+|[\\s\\r\\n]+$)", "");
		return res;
	}

	static public String suRunScript(Context ctx, String dir, int scriptId) throws Exception {
		String script = ctx.getResources().getResourceEntryName(scriptId);
		File f = ctx.getFileStreamPath(script);
		if (!f.exists()) {
			InputStream raw = ctx.getResources().openRawResource(scriptId);
			BufferedReader is = new BufferedReader(new InputStreamReader(raw, "UTF-8"));
			BufferedWriter os = new BufferedWriter(new OutputStreamWriter(ctx.openFileOutput(script, Context.MODE_PRIVATE), "UTF-8"));
			while (is.ready()) {
				String line = is.readLine();
				os.write(line + "\n");
			}
			is.close();
			os.close();
		}
		String sh = findBusybox();
		if (sh == null) {
			sh = "sh";
		} else {
			sh += " sh";
		}
		return suRun(dir, sh + " '" + f.getAbsolutePath() + "'");
	}

	static public boolean canReboot() {
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

	static public void rebootApi(Context ctx, String reason) throws Exception {
		if (!canReboot()) {
			throw new Exception("Reboot not supported");
		}
		PowerManager pm = (PowerManager)ctx.getSystemService(Context.POWER_SERVICE);
		reboot.invoke(pm, (Object)null);
	}

	static public void rebootCommand(String reason) throws Exception {
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

	static public String findBusybox() {
		return pathFind("busybox");
	}
}
