package com.achep.FifteenPuzzle.updater;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import com.achep.FifteenPuzzle.NotificationUtils;
import com.achep.FifteenPuzzle.R;
import com.achep.FifteenPuzzle.Utils;
import com.achep.FifteenPuzzle.preferences.Settings;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.widget.RemoteViews;

public class DownloadService extends Service {

	private NotificationManager mNotificationManager;
	private RemoteViews mDownloadingRVs;
	private Notification mDownloadingNotify;

	private String mVersionName;

	@Override
	public void onCreate() {
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	}

	public void onStart(Intent intent, int startId) {
		mVersionName = intent.getAction();
		if (mVersionName != null) {
			new AsyncDownloadAndInstall().execute();
		} else {
			stopSelf();
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private class AsyncDownloadAndInstall extends
			AsyncTask<Void, Float, String> {

		private int mFileSize = 0;
		private long mStartTime = 0;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			showDownloadingNotify();
		}

		@Override
		protected void onProgressUpdate(Float... progress) {
			if (mStartTime == 0)
				mStartTime = getCurrentTimeMillis();

			int progressI = (int) progress[0].floatValue();
			float progressF = progress[0].floatValue();
			int deltaTime = (int) (getCurrentTimeMillis() - mStartTime);
			if (deltaTime < 1000)
				return;

			mDownloadingRVs.setTextViewText(
					R.id.eta,
					"ETA: "
							+ Utils.getFormatedTime((long) (100f / progressF
									* deltaTime - deltaTime)));
			mDownloadingRVs.setTextViewText(R.id.progresstext, progressI + "%");
			mDownloadingRVs.setProgressBar(R.id.progressbar, 100, progressI,
					false);
			pushNotify(mDownloadingNotify);
		}

		@Override
		protected String doInBackground(Void... void_) {
			Resources res = getResources();
			if (Utils.connectedToInternet(DownloadService.this)) {
				URL url = null;
				try {
					url = new URL(
							"https://github.com/AChep/FifteenPuzzle/raw/master/bin/FifteenPuzzle.apk");
					URLConnection conexion = url.openConnection();
					conexion.connect();
					mFileSize = conexion.getContentLength();
				} catch (Exception e) {
					// TODO: Do something
					return "5";
				}

				// Open input stream
				InputStream input = null;
				try {
					input = new BufferedInputStream(url.openStream());
				} catch (IOException e) {
					// TODO: Do something
					return "4";
				}

				// Open output stream
				FileOutputStream output = null;
				try {
					output = new FileOutputStream(new File(
							Utils.getPathToFolder(Settings.SDCARD_FOLDER),
							mVersionName + ".apk"));
				} catch (FileNotFoundException e) {
					// TODO: Do something
					return "3";
				}

				byte data[] = new byte[1024];
				long total = 0;
				int count;
				try {
					while ((count = input.read(data)) != -1) {
						total += count;
						output.write(data, 0, count);

						publishProgress((total * 100) / (float) mFileSize);
					}
				} catch (IOException e) {
					// TODO: Do something
					return "2";
				}

				try {
					input.close();
					output.flush();
					output.close();
				} catch (IOException e) {
					// TODO: Do something
					return "1";
				}

				return null;
			} else
				return res
						.getString(R.string.auto_updater_downloading_error_connection);

		}

		@Override
		protected void onPostExecute(String error) {
			if (error != null) {
				showErrorNotify();
			} else {
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setDataAndType(Uri.fromFile(new File(Utils
						.getPathToFolder(Settings.SDCARD_FOLDER), mVersionName
						+ ".apk")), "application/vnd.android.package-archive");
				startActivity(intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

				// We're updated now!
				cancelNotify();
			}

			// Die, potato!
			stopSelf();
		}

		private long getCurrentTimeMillis() {
			return System.currentTimeMillis();
		}
	}

	private void showErrorNotify() {
		cancelNotify();
		pushNotify(NotificationUtils.getNotification(
				this,
				mVersionName,
				getResources().getString(
						R.string.download_service_notification_error_text),
				R.drawable.ic_actionbar_retry, PendingIntent.getService(this,
						0, new Intent(this, this.getClass())
								.setAction(mVersionName), 0), PendingIntent
						.getActivity(this, 0, new Intent(this,
								AutoUpdater.class),
								Intent.FLAG_ACTIVITY_NEW_TASK)));
	}

	private void showDownloadingNotify() {
		Notification n = new Notification();

		Resources res = getResources();
		String contentTitle = mVersionName;
		String contentText = res
				.getString(R.string.download_service_notification_downloading_text);

		mDownloadingRVs = new RemoteViews(getPackageName(),
				R.layout.notification_download_service_progress);
		mDownloadingRVs.setTextViewText(R.id.title, contentTitle);

		n.contentView = mDownloadingRVs;
		n.contentIntent = PendingIntent.getActivity(this, 0, new Intent(this,
				AutoUpdater.class), Intent.FLAG_ACTIVITY_NEW_TASK);
		n.icon = R.drawable.ic_statusbar_new_version;
		n.tickerText = contentText;
		n.flags = Notification.FLAG_ONGOING_EVENT;

		mDownloadingNotify = n;

		cancelNotify();
		pushNotify(mDownloadingNotify);
	}

	private void pushNotify(Notification n) {
		mNotificationManager.notify(NotificationUtils.DOWNLOAD_SERVICE, n);
	}

	private void cancelNotify() {
		mNotificationManager.cancel(NotificationUtils.DOWNLOAD_SERVICE);
	}
}