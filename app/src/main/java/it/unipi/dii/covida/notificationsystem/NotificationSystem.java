package it.unipi.dii.covida.notificationsystem;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import it.unipi.dii.covida.locationservice.LocationService;
import it.unipi.dii.covida.MainActivity;
import it.unipi.dii.covida.R;
import it.unipi.dii.covida.ui.settings.SettingsActivity;

import static android.content.Context.NOTIFICATION_SERVICE;

/**
 * This class is responsible to handle the notifications coming from the application
 */
public class NotificationSystem {

    private static final String CHANNEL_ID = "CovidA";
    private static final int MAIN_NOTIFICATION_ID = 0xfe;

    private static boolean initialized = false;
    private static Context initialContext;
    private static NotificationManager notificationManager;
    private static Notification mainNotification;
    private static NotificationChannel notificationChannel;

    /**
     * This function initialize the notification channel of the application
     * @param context the application context
     */
    public static void init(Context context) {
        if(initialized)
            return;
        initialContext = context;
        notificationChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT);
        notificationChannel.setDescription(CHANNEL_ID);
        notificationChannel.setSound(null, null); // avoid bothering the user!
        notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(notificationChannel);
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
        mainNotification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("CovidA")
                .setContentText("NotificationSystem initialized")
                .setChannelId(CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_gps_fixed_black_24dp)
                .setContentIntent(pendingIntent)
                .build();
        notificationManager.notify(MAIN_NOTIFICATION_ID, mainNotification); // always use the same id: i want to OVERWRITE the same notification!
        initialized = true;
    }

    /**
     * This function returns the main notification of the application
     * @return the main notification
     */
    public static Notification getMainNotification() {
        return mainNotification;
    }

    /**
     * This function returns the id of the main notification of the application
     * @return the id of the main notification
     */
    public static int getMainNotificationId() {
        return MAIN_NOTIFICATION_ID;
    }

    public static void close() {
        notificationManager.cancelAll();
        initialContext = null;
        notificationManager = null;
        mainNotification = null;
        notificationChannel = null;
        initialized = false;
    }

    /**
     * This function change the content of the main notification
     * @param update the string you want to show to the user through the notification
     * @param context the context to utilize
     */
    public static void updateNotificationContent(String update, Context context) {
        mainNotification = createNotification(update, context, MAIN_NOTIFICATION_ID);
    }

    /**
     * This function change the content of the main notification
     * @param update the string you want to show to the user through the notification
     */
    public static void updateNotificationContent(String update) {
        updateNotificationContent(update, initialContext);
    }

    /**
     * This function create a new notification identified by notificationId
     * @param contentText the string you want to show to the user through the notification
     * @param context the context to utilize
     * @param notificationId the notification identifier
     */
    private static Notification createNotification(String contentText, Context context, int notificationId) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context,0, notificationIntent, 0);
        Intent settingsIntent = new Intent(context, SettingsActivity.class);
        PendingIntent settingsPendingIntent = PendingIntent.getActivity(context,0, settingsIntent, 0);
        Notification newNotification;

        if(LocationService.isRunning()){
            newNotification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("Location tracker")
                    //.setContentText(update)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                    .setChannelId(CHANNEL_ID)
                    .setColor(context.getColor(R.color.colorAccent))
                    .setSmallIcon(R.drawable.ic_gps_fixed_black_24dp)
                    .setContentIntent(pendingIntent)
                    .addAction(0, "SETTINGS", settingsPendingIntent)
                    .build();
        }else{
            newNotification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("Location tracker")
                    .setContentText(contentText)
                    .setChannelId(CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_gps_fixed_black_24dp)
                    .setContentIntent(pendingIntent)
                    .addAction(0, "SETTINGS", settingsPendingIntent)
                    .build();
        }
        notificationManager.notify(notificationId, newNotification);
        return newNotification;
    }

    /**
     * This function remove a notification, given its id
     * @param notificationId the notification identifier
     */
    public static void removeNotification(int notificationId) {
        notificationManager.cancel(notificationId);
    }

}
