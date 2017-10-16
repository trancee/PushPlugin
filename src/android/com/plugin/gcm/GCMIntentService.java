package com.plugin.gcm;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.media.RingtoneManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;

import com.google.android.gcm.GCMBaseIntentService;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

	private static final String TAG = "GCMIntentService";

	private static String packageName = null;

	private static float devicePixelRatio = 1.0f;

	private static int LargeIconSize = 256;
	private static int BigPictureSize = 640;

	public GCMIntentService() {
		super("GCMIntentService");
	}

	@Override
	public void onRegistered(Context context, String regId) {

		Log.v(TAG, "onRegistered: "+ regId);

		JSONObject json;

		try
		{
			json = new JSONObject().put("event", "registered");
			json.put("regid", regId);

			Log.v(TAG, "onRegistered: " + json.toString());

			// Send this JSON data to the JavaScript application above EVENT should be set to the msg type
			// In this case this is the registration ID
			PushPlugin.sendJavascript( json );

		}
		catch( JSONException e)
		{
			// No message to the user is sent, JSON failed
			Log.e(TAG, "onRegistered: JSON exception");
		}
	}

	@Override
	public void onUnregistered(Context context, String regId) {
		Log.d(TAG, "onUnregistered - regId: " + regId);
	}

	@Override
	protected void onMessage(Context context, Intent intent) {
		Log.d(TAG, "onMessage - context: " + context);

		// Extract the payload from the message
		Bundle extras = intent.getExtras();
		if (extras != null)
		{
			// if we are in the foreground, just surface the payload, else post it to the statusbar
			if (PushPlugin.isInForeground()) {
				extras.putBoolean("foreground", true);
				PushPlugin.sendExtras(extras);
			}
			else {
				extras.putBoolean("foreground", false);

				// Send a notification if there is a message
				if (extras.getString("message") != null && extras.getString("message").length() != 0) {
					createNotification(context, extras);
				}
			}
		}
	}

	public void createNotification(Context context, Bundle extras)
	{
		packageName = context.getPackageName();

		DisplayMetrics metrics = new DisplayMetrics();
		WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
		windowManager.getDefaultDisplay().getMetrics(metrics);
		devicePixelRatio = metrics.density;
		Log.d(TAG, "devicePixelRatio: "+ devicePixelRatio);

		String appName = getAppName(this);

		// which version of the push notifications are we dealing with? (for backwards compatibility)
		int v = Integer.parseInt(extras.getString("v", "1"));

		int notificationId = 0;

		try {
			Random rand = new Random();
			notificationId = Integer.parseInt(extras.getString("notificationId", String.valueOf(rand.nextInt(1000))));
		}
		catch(NumberFormatException e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID: " + e.getMessage());
		}
		catch(Exception e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID" + e.getMessage());
		}

		Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		notificationIntent.putExtra("pushBundle", extras);

		PendingIntent contentIntent = PendingIntent.getActivity(this, notificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		//int defaults = extras.getInt("defaults", (Notification.DEFAULT_ALL));

		NotificationCompat.Builder notification =
			new NotificationCompat.Builder(context)
				// Set which notification properties will be inherited from system defaults.
				//.setDefaults(defaults)
				// Set the "ticker" text which is displayed in the status bar when the notification first arrives.
				.setTicker(extras.getString("summary", extras.getString("title")))
				// Set the first line of text in the platform notification template.
				.setContentTitle(extras.getString("title"))
				// Set the second line of text in the platform notification template.
				.setContentText(
					(v >= 2) ?
							extras.getString("summary")
						:
							extras.getString("message")
				)
				// Set the third line of text in the platform notification template.
				.setSubText(
					(v >= 2) ?
						null
					:
						extras.getString("summary")
				)
				// Supply a PendingIntent to be sent when the notification is clicked.
				.setContentIntent(contentIntent)
				// Set the large number at the right-hand side of the notification.
				.setNumber(Integer.parseInt(extras.getString("badge", extras.getString("msgcnt", "0"))))
				// A variant of setSmallIcon(int) that takes an additional level parameter for when the icon is a LevelListDrawable.
				.setSmallIcon(getSmallIcon(extras.getString("smallIcon"), context.getApplicationInfo().icon))
				// Add a large icon to the notification (and the ticker on some devices).
				.setLargeIcon(
					getLargeIcon(this,
						(v >= 2) ?
							extras.getString("avatar", "https://img.andygreen.com/image.cf?Width=" + LargeIconSize + "&Path=avatar.png")
						:
							extras.getString("icon")
					)
				)
				// Vibrate constantly for the specified period of time.
				.setVibrate(new long[] {
					// The first value indicates the number of milliseconds to wait before turning the vibrator on.
					100,		// Off
					// The next value indicates the number of milliseconds for which to keep the vibrator on before turning it off.
					100,	// On
					// Subsequent values alternate between durations in milliseconds to turn the vibrator off or to turn the vibrator on.
					150,	// Off
					150,	// On
					300		// Off
				})
				// Set the desired color for the indicator LED on the device, as well as the blink duty cycle (specified in milliseconds).
				.setLights(getColor(extras.getString("led", "000000")), 500, 500)
				// Make this notification automatically dismissed when the user touches it.
				.setPriority(Notification.PRIORITY_DEFAULT)
				.setAutoCancel(extras.getBoolean("autoCancel", true))
			;

		Uri sound = getSound(extras.getString("sound"));
		if (sound != null) {
			// Set the sound to play.
			notification.setSound(sound);
		}

		if (Build.VERSION.SDK_INT >= 16) {
			String message = (v >= 2) ? extras.getString("summary") : extras.getString("message");
			String pictureUrl = (v >= 2) ? null : extras.getString("picture");
			if (pictureUrl != null && pictureUrl.length() > 0) {
				// Add a rich notification style to be applied at build time.
				notification.setStyle(
					new NotificationCompat.BigPictureStyle()
						// Overrides ContentTitle in the big form of the template.
						.setBigContentTitle(extras.getString("title"))
						// Set the first line of text after the detail section in the big form of the template.
						.setSummaryText(message)
						// Override the large icon when the big notification is shown.
						.bigLargeIcon(getLargeIcon(this, extras.getString("avatar", "https://img.andygreen.com/image.cf?Width=" + LargeIconSize + "&Path=avatar.png")))
						// Provide the bitmap to be used as the payload for the BigPicture notification.
						.bigPicture(getPicture(this, pictureUrl))
					);

				// remove the third line as this is confusing for BigPicture style.
				notification.setSubText(null);
			} else if (message != null && message.length() > 30) {
				// Add a rich notification style to be applied at build time.
				notification.setStyle(
					new NotificationCompat.BigTextStyle()
						// Overrides ContentTitle in the big form of the template.
						.setBigContentTitle(extras.getString("title"))
						// Provide the longer text to be displayed in the big form of the template in place of the content text.
						.bigText(message)
						// Set the first line of text after the detail section in the big form of the template.
						.setSummaryText(
							(v >= 2) ?
								null
							:
								extras.getString("summary")
						)
					);
			}
		}

		final Notification build = notification.build();

		build.flags =
			/*
			// Use all default values (where applicable).
			Notification.DEFAULT_ALL |

			// Use the default notification lights.
			Notification.DEFAULT_LIGHTS |
			// Use the default notification sound.
			Notification.DEFAULT_SOUND |
			// Use the default notification vibrate.
			Notification.DEFAULT_VIBRATE |
			*/
			// Set if you want the LED on for this notification.
			Notification.FLAG_SHOW_LIGHTS |

			// Notification should be canceled when it is clicked by the user.
			Notification.FLAG_AUTO_CANCEL
			;

		((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify((String) appName, notificationId, build);
	}

	/**
	 * Returns the path of the notification's sound file
	 */
	private static Uri getSound(String sound) {
		if (sound != null) {
			try {
				int soundId = (Integer) RingtoneManager.class.getDeclaredField(sound).get(Integer.class);

				return RingtoneManager.getDefaultUri(soundId);
			} catch (Exception e) {
				return Uri.parse(sound);
			}
		}

		return null;
	}
	/**
	 * Returns the small icon's ID
	 */
	private static int getSmallIcon(String iconName, int iconId)
	{
		int resId = 0;

		resId = getIconValue(packageName, iconName);

		if (resId == 0) {
			resId = getIconValue("android", iconName);
		}

		if (resId == 0) {
			resId = getIconValue(packageName, "icon");
		}

		if (resId == 0) {
			return iconId;
		}

		return resId;
	}
	/**
	 * Returns the icon's ID
	 */
	private static Bitmap getLargeIcon(Context context, String icon)
	{
		Bitmap bmp = null;

		if (icon != null) {
			if (icon.startsWith("http://") || icon.startsWith("https://")) {
				bmp = getIconFromURL(icon);
			} else if (icon.startsWith("file://")) {
				bmp = getIconFromURI(context, icon);
			} else {
				bmp = getIconFromURL("https://img.andygreen.com/photo.cf?Width=" + LargeIconSize + "&Height=" + LargeIconSize + "&Ratio=" + devicePixelRatio + "&Checksum=" + icon);
			}

			if (bmp == null) {
				bmp = getIconFromRes(context, icon);
			}
		} else {
			bmp = BitmapFactory.decodeResource(context.getResources(), context.getApplicationInfo().icon);
		}

		return bmp;
	}
	/**
	 * Returns the bitmap
	 */
	private static Bitmap getPicture(Context context, String pictureUrl)
	{
		Bitmap bmp = null;

		if (pictureUrl != null) {
			if (pictureUrl.startsWith("http://") || pictureUrl.startsWith("https://")) {
				bmp = getIconFromURL(pictureUrl);
			} else if (pictureUrl.startsWith("file://")) {
				bmp = getIconFromURI(context, pictureUrl);
			} else {
				bmp = getIconFromURL("https://img.andygreen.com/photo.cf?Width=" + BigPictureSize + "&Ratio=" + devicePixelRatio + "&Checksum=" + pictureUrl);
			}

			if (bmp == null) {
				bmp = getIconFromRes(context, pictureUrl);
			}
		}

		return bmp;
	}
	/**
	 * @return
	 *	The notification color for LED
	 */
	private static int getColor(String hexColor)
	{
		int aRGB = Integer.parseInt(hexColor,16);

		aRGB += 0xFF000000;

		return aRGB;
	}

	private static String getAppName(Context context)
	{
		CharSequence appName =
				context
					.getPackageManager()
					.getApplicationLabel(context.getApplicationInfo());

		return (String)appName;
	}




	/**
	 * Returns numerical icon Value
	 *
	 * @param {String} className
	 * @param {String} iconName
	 */
	private static int getIconValue (String className, String iconName) {
		int icon = 0;

		try {
			Class<?> klass = Class.forName(className + ".R$drawable");

			icon = (Integer) klass.getDeclaredField(iconName).get(Integer.class);
		} catch (Exception e) {}

		return icon;
	}

	/**
	 * Converts an resource to Bitmap.
	 *
	 * @param icon
	 *	The resource name
	 * @return
	 *	The corresponding bitmap
	 */
	private static Bitmap getIconFromRes (Context context, String icon) {
		Resources res = context.getResources();
		int iconId = 0;

		iconId = getIconValue(packageName, icon);

		if (iconId == 0) {
			iconId = getIconValue("android", icon);
		}

		if (iconId == 0) {
			iconId = android.R.drawable.ic_menu_info_details;
		}

		Bitmap bmp = BitmapFactory.decodeResource(res, iconId);

		return bmp;
	}

	/**
	 * Converts an Image URL to Bitmap.
	 *
	 * @param src
	 *	The external image URL
	 * @return
	 *	The corresponding bitmap
	 */
	private static Bitmap getIconFromURL (String src) {
		Bitmap bmp = null;
		ThreadPolicy origMode = StrictMode.getThreadPolicy();

		try {
			URL url = new URL(src);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();

			StrictMode.ThreadPolicy policy =
					new StrictMode.ThreadPolicy.Builder().permitAll().build();

			StrictMode.setThreadPolicy(policy);

			connection.setDoInput(true);
			connection.connect();

			InputStream input = connection.getInputStream();

			bmp = BitmapFactory.decodeStream(input);
		} catch (Exception e) {
			e.printStackTrace();
		}

		StrictMode.setThreadPolicy(origMode);

		return bmp;
	}

	/**
	 * Converts an Image URI to Bitmap.
	 *
	 * @param src
	 *	The internal image URI
	 * @return
	 *	The corresponding bitmap
	 */
	private static Bitmap getIconFromURI (Context context, String src) {
		AssetManager assets = context.getAssets();
		Bitmap bmp = null;

		try {
			String path = src.replace("file:/", "www");
			InputStream input = assets.open(path);

			bmp = BitmapFactory.decodeStream(input);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return bmp;
	}

	@Override
	public void onError(Context context, String errorId) {
		Log.e(TAG, "onError - errorId: " + errorId);
	}
}