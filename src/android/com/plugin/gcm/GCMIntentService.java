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

import java.net.HttpURLConnection;
import java.net.URL;

import com.google.android.gcm.GCMBaseIntentService;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

	private static final String TAG = "GCMIntentService";
	
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
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		String appName = getAppName(this);

		Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		notificationIntent.putExtra("pushBundle", extras);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		int defaults = Notification.DEFAULT_ALL;

		if (extras.getString("defaults") != null) {
			try {
				defaults = Integer.parseInt(extras.getString("defaults"));
			} catch (NumberFormatException e) {}
		}

		NotificationCompat.Builder mBuilder =
			new NotificationCompat.Builder(context)
				.setDefaults(defaults)
				.setTicker(extras.getString("title"))
				.setContentTitle(extras.getString("title"))
				.setContentText(extras.getString("message"))
				.setContentIntent(contentIntent)
				.setNumber(extras.getInt("msgcnt", extras.getInt("badge", 0)))
				.setSmallIcon(getSmallIcon(extras.getString("smallIcon"), context.getApplicationInfo().icon))
				.setLargeIcon(getLargeIcon(extras.getString("icon")))
				.setLights(getColor(extras.getString("led", "000000")), 500, 500)
				// .setWhen(System.currentTimeMillis())
				.setAutoCancel(extras.getBoolean("autoCancel", true));

		if (Build.VERSION.SDK_INT > 16) {
			mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(extras.getString("message")));
		}

		Uri sound = getSound(extras.getString("sound"));
		if (sound != null) {
			mBuilder.setSound(sound);
		}

		int notId = 0;

		try {
			notId = Integer.parseInt(extras.getString("notId"));
		}
		catch(NumberFormatException e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID: " + e.getMessage());
		}
		catch(Exception e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID" + e.getMessage());
		}

		mNotificationManager.notify((String) appName, notId, mBuilder.build());
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
	private static String getSmallIcon(String iconName, String icon)
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
			return icon;
		}

		return resId;
	}
    /**
     * Returns the icon's ID
     */
	private static String getLargeIcon(String icon)
	{
		Bitmap bmp = null;

		if (icon != null) {
			if (icon.startsWith("http://") || icon.startsWith("https://")) {
				bmp = getIconFromURL(icon);
			} else if (icon.startsWith("file://")) {
				bmp = getIconFromURI(icon);
			}

			if (bmp == null) {
				bmp = getIconFromRes(icon);
			}
		}

		return bmp;
	}
    /**
     * @return
     *      The notification color for LED
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
    private int getIconValue (String className, String iconName) {
        int icon = 0;

        try {
            Class<?> klass  = Class.forName(className + ".R$drawable");

            icon = (Integer) klass.getDeclaredField(iconName).get(Integer.class);
        } catch (Exception e) {}

        return icon;
    }

    /**
     * Converts an resource to Bitmap.
     *
     * @param icon
     *      The resource name
     * @return
     *      The corresponding bitmap
     */
    private Bitmap getIconFromRes (String icon) {
        Resources res = LocalNotification.context.getResources();
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
     *      The external image URL
     * @return
     *      The corresponding bitmap
     */
    private Bitmap getIconFromURL (String src) {
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
     *      The internal image URI
     * @return
     *      The corresponding bitmap
     */
    private Bitmap getIconFromURI (String src) {
        AssetManager assets = LocalNotification.context.getAssets();
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
