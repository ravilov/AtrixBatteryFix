package hr.ravilov.atrixbatteryfix;

import java.lang.Thread.UncaughtExceptionHandler;

public class MyExceptionCatcher implements UncaughtExceptionHandler {
	private UncaughtExceptionHandler def;

	public MyExceptionCatcher() {
		def = Thread.getDefaultUncaughtExceptionHandler();
	}

	public void uncaughtException(Thread t, Throwable e) {
		/* do the stuff */
		def.uncaughtException(t, e);
	}

/*
	private void writeToFile(String stacktrace, String filename) {
		try {
			BufferedWriter bos = new BufferedWriter(new FileWriter(localPath + "/" + filename));
			bos.write(stacktrace);
			bos.flush();
			bos.close();
		}
		catch (Exception ex) {
			e.printStackTrace();
		}
	}

	private void sendToServer(String stacktrace, String filename) {
		DefaultHttpClient httpClient = new DefaultHttpClient();
		HttpPost httpPost = new HttpPost(url);
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("filename", filename));
		nvps.add(new BasicNameValuePair("stacktrace", stacktrace));
		try {
			httpPost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
			httpClient.execute(httpPost);
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
	}
*/
}
