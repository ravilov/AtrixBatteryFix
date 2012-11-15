package hr.ravilov.atrixbatteryfix;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

public class ShellInterface {
	final private static String TAG = "<shell>";
	final private static String DIED = "shell process died";

	private MyUtils utils;
	private String shell = null;
	private String dir = null;
	private volatile Process process = null;
	private volatile DataOutputStream stdin = null;
	private volatile DataInputStream stdout = null;
	private volatile DataInputStream stderr = null;
	private volatile boolean runningCommand = false;

	public ShellInterface(MyUtils u, String sh, String d) throws Exception {
		utils = u;
		shell = sh;
		dir = d;
		if (shell == null || shell.equals("")) {
			throw new Exception("shell not defined");
		}
		open();
	}

	public ShellInterface(MyUtils u, String sh) throws Exception {
		this(u, sh, null);
	}

	protected void open() throws Exception {
		utils.log(TAG, "opening new shell interface for [%s]", shell);
		synchronized (this) {
			process = (dir == null) ? Runtime.getRuntime().exec(shell) : Runtime.getRuntime().exec(shell, null, new File(dir));
			stdin = new DataOutputStream(process.getOutputStream());
			stdout = new DataInputStream(process.getInputStream());
			stderr = new DataInputStream(process.getErrorStream());
		}
		try {
			long time = System.currentTimeMillis();
			while (System.currentTimeMillis() < time + 300) {
				Thread.yield();
			}
		}
		catch (Exception ex) { }
		String res = "";
		while (hasError()) {
			res += getError(false);
		}
		while (hasOutput()) {
			res += getOutput(false);
		}
		try {
			checkProcess();
		}
		catch (Exception ex) { }
		res = trim(res);
		if (!res.equals("")) {
			close();
			throw new Exception(res);
		}
		if (!isProcessAlive()) {
			close();
			throw new Exception(DIED);
		}
	}

	public void close() {
		utils.log(TAG, String.format("closing all shell channels"));
		closeStdin();
		closeStdout();
		closeStderr();
		closeProcess();
	}

	protected boolean checkProcess() throws Exception {
		if (isProcessAlive()) {
			return true;
		}
		if (process != null) {
			close();
			throw new Exception(DIED);
		}
		return false;
	}

	protected boolean isProcessAlive() {
		if (process == null) {
			return false;
		}
		boolean tmp = false;
		try {
			process.exitValue();
		}
		catch (Exception ex) {
			tmp = true;
		}
		if (!tmp) {
			return false;
		}
		return true;
	}

	protected boolean isStdinAlive() {
		if (stdin == null) {
			return false;
		}
		if (process == null) {
			return false;
		}
		boolean tmp = false;
		try {
			stdin.flush();
		}
		catch (IOException ex) {
			tmp = true;
		}
		if (tmp) {
			return false;
		}
		return true;
	}

	protected boolean isStdoutAlive() {
		if (stdout == null) {
			return false;
		}
		boolean tmp = false;
		try {
			stdout.available();
		}
		catch (IOException ex) {
			tmp = true;
		}
		if (tmp) {
			return false;
		}
		return true;
	}

	protected boolean isStderrAlive() {
		if (stderr == null) {
			return false;
		}
		boolean tmp = false;
		try {
			stderr.available();
		}
		catch (IOException ex) {
			tmp = true;
		}
		if (tmp) {
			return false;
		}
		return true;
	}

	protected void closeStdin() {
		if (stdin == null) {
			return;
		}
		try {
			stdin.flush();
			stdin.close();
		}
		catch (Exception ex) { }
		synchronized (this) {
			stdin = null;
		}
	}

	protected void closeStdout() {
		if (stdout == null) {
			return;
		}
		try {
			stdout.close();
		}
		catch (Exception ex) { }
		synchronized (this) {
			stdout = null;
		}
	}

	protected void closeStderr() {
		if (stderr == null) {
			return;
		}
		try {
			stderr.close();
		}
		catch (Exception ex) { }
		synchronized (this) {
			stderr = null;
		}
	}

	protected void closeProcess(boolean force) {
		if (process == null) {
			return;
		}
		if (force) {
			try {
				process.destroy();
			}
			catch (Exception ex) { }
		}
		try {
			process.waitFor();
		}
		catch (Exception ex) { }
		synchronized (this) {
			process = null;
		}
	}

	protected void closeProcess() {
		closeProcess(true);
	}

	public void finish() {
		closeStdin();
		closeProcess(false);
	}

	public boolean sendCommand(String cmd, boolean force) throws Exception {
		if (!checkProcess()) {
			return false;
		}
		while (runningCommand && !force) {
			try {
				long time = System.currentTimeMillis();
				while (System.currentTimeMillis() < time + 100) {
					Thread.yield();
				}
			}
			catch (Exception ex) { }
		}
		if (!isStdinAlive()) {
			utils.log(TAG, "stream closed, cannot send command");
			closeStdin();
			return false;
		}
		utils.log(TAG, String.format("sending command [%s]", cmd));
		try {
			stdin.writeBytes(cmd + "\n");
			stdin.flush();
		}
		catch (Exception ex) {
			return false;
		}
		return true;
	}

	public boolean sendCommand(String cmd) throws Exception {
		return sendCommand(cmd, false);
	}

	public boolean hasOutput() throws Exception {
		if (!isStdoutAlive()) {
			if (stdout == null) {
				return false;
			} else {
				closeStdout();
				throw new Exception(DIED);
			}
		}
		try {
			return (stdout.available() > 0) ? true : false;
		}
		catch (Exception ex) { }
		return false;
	}

	public boolean hasError() throws Exception {
		if (!isStderrAlive()) {
			if (stderr == null) {
				return false;
			} else {
				closeStderr();
				throw new Exception(DIED);
			}
		}
		try {
			return (stderr.available() > 0) ? true : false;
		}
		catch (Exception ex) { }
		return false;
	}

	public void waitForOutput() throws Exception {
		while (!hasOutput()) {
			try {
				Thread.yield();
			}
			catch (Exception ex) { }
		}
	}

	public void waitForError() throws Exception {
		while (!hasError()) {
			try {
				Thread.yield();
			}
			catch (Exception ex) { }
		}
	}

	public void waitForAny() throws Exception {
		while (!hasOutput() && !hasError()) {
			try {
				Thread.yield();
			}
			catch (Exception ex) { }
		}
	}

	public String getOutput(boolean block) throws Exception {
		try {
			if (hasOutput() || block) {
				return stdout.readLine() + "\n";
			}
		}
		catch (Exception ex) {
			return null;
		}
		return "";
	}

	public String getOutput() throws Exception {
		return getOutput(true);
	}

	public String getError(boolean block) throws Exception {
		try {
			if (hasError() || block) {
				return stderr.readLine() + "\n";
			}
		}
		catch (Exception ex) {
			return null;
		}
		return "";
	}

	public String getError() throws Exception {
		return getError(true);
	}

	public String sendAndReceive(String cmd) throws Exception {
		while (runningCommand) {
			try {
				Thread.yield();
			}
			catch (Exception ex) { }
		}
		synchronized (this) {
			runningCommand = true;
		}
		if (!sendCommand(cmd, true)) {
			return null;
		}
/*
		try {
			//Thread.sleep(350);
			long time = System.currentTimeMillis();
			while (System.currentTimeMillis() < time + 350) {
				Thread.yield();
			}
		}
		catch (Exception ex) { }
*/
		waitForAny();
		String out = "";
		while (hasOutput() || out.equals("")) {
			out += getOutput(true);
		}
		String err = "";
		while (hasError()) {
			err += getError(true);
		}
		synchronized (this) {
			runningCommand = false;
		}
		err = trim(err);
		if (!err.equals("")) {
			throw new Exception(err);
		}
		out = trim(out);
		return out;
	}

	public static String trim(String str) {
		return str.replaceAll("(^[\\s\\r\\n]+|[\\s\\r\\n]+$)", "");
	}
}
