package ucsd.skulkarn.caffeineapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.*;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CaffeineService extends TileService {

    private static final int MAX_TIME = 3600000;

    private static final int TIMES[] = {
            0, // clear the timer
            30000, // 30 seconds -- TEST
            300000, // 5 minutes
            900000, // 15 minutes
            1800000, // 30 minutes
            3600000, // 1 hour
            -1 // infinite time
    };

    int index = 0;

    private static final String tag = "CaffeineService";
    private WakeLock lock = null;
    private Handler updateTile = new Handler();
    private ScheduledFuture<?> future;
    private boolean SHOW_TOAST = false, REQUIRES_INIT = true;

    private Runnable r = new Runnable() {
        @Override
        public void run() {
            // if lock == null, retry later
            if(lock == null){
                return;
            }
            //update the tile every second
            Log.d(tag, "scheduled at fixed rate, delay = " + delay);
            PowerManager pm = (PowerManager)getBaseContext().getSystemService(Context.POWER_SERVICE);
            if(!pm.isScreenOn())
                Log.d(tag, "Screen is off");
            if(!lock.isHeld())
                Log.d(tag, "Lock released at some time in the last second");
            Tile tile = getQsTile();
            tile.setState(Tile.STATE_ACTIVE);
            if(delay == -1){
                tile.setLabel("Unlimited");
                tile.updateTile();
                SHOW_TOAST = true;
                return;
            }
            else
                tile.setLabel(String.format(Locale.US,"%02d:%02d",
                        delay/60000, ((delay - delay/60000*60000)/1000)));
            delay = delay - 1000;
            tile.updateTile();

            //keep looping
            if(delay > 0)
                return;
            //stop looping
            else{
                // display a completion message to the user
                tile.setLabel("Caffination complete!");
                tile.setState(Tile.STATE_INACTIVE);
                tile.updateTile();
                // reset index so user can start caffinating again
                index = 0;
                //release the wakelock and stop all callbacks.
                Log.d(tag, "releasing wakelock manually");
                lock.release();
                future.cancel(true);
                future = null;
                stopForeground(true);
                REQUIRES_INIT = true;
            }
        }
    };

    private int delay;

    public CaffeineService() {
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        Log.d(tag, "started listening");
        if(REQUIRES_INIT){
            Tile tile = getQsTile();
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("Start Caffinating!");
            tile.updateTile();
        }
    }
    @Override
    public void onStopListening() {
        super.onStopListening();
        if(SHOW_TOAST) {
            Toast.makeText(this, "Unlimited Mode can cause large battery drain",
                    Toast.LENGTH_LONG).show();
            SHOW_TOAST = false;
        }
        Log.d(tag, "stopped listening");
    }

    @Override
    public void onClick() {
        super.onClick();
        delay = index >= TIMES.length - 1 ? TIMES[(index=0)] : TIMES[++index];
        Log.d(tag, "setting delay to " + delay);

        // clear previous tile updates
        updateTile.removeCallbacks(r);

        if(delay == 0){
            //set the counting status
            future.cancel(true);
            future = null;
            //update the tile
            Tile tile = getQsTile();
            tile.setLabel("Start Caffinating!");
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
            // check if the lock is already held.
            if(lock != null && lock.isHeld()){
                Log.d(tag, "releasing old wakelock");
                //release the lock AND clear the timer if changing the amount of time
                lock.release();
                SHOW_TOAST = false;
            }
            Log.d(tag, "cancelling notification");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.cancel(1);
            stopForeground(true);
            return;

        }

        //start main caffeine service by intent
        Intent intent = new Intent(this, CaffeineService.class);
        Log.d(tag, "starting CaffeineService by Intent");
        if(Build.VERSION.SDK_INT > 25) {
            Log.d(tag, "foreground, " + Build.VERSION.SDK_INT);
            startForegroundService(intent);
        }
        else {
            startService(intent);
            Log.d(tag, "regular, " + Build.VERSION.SDK_INT);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // call the parent method
        super.onStartCommand(intent, flags, startId);

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Channel";
            String description = "Notify the User";
            int importance = NotificationManager.IMPORTANCE_NONE;
            NotificationChannel channel = new NotificationChannel("Caffeine", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, "Caffeine")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("Caffination!")
                .setContentText("Caffination in Progress")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).build();

        startForeground(1, notification);



        // obtain power manager to create wakelock
        PowerManager pm = ((PowerManager) getSystemService(Context.POWER_SERVICE));

        // check if the lock is already held.
        if(lock != null && lock.isHeld()){
            Log.d(tag, "releasing old wakelock");
            //release the lock AND clear the timer if changing the amount of time
            lock.release();
        }

        //acquire the wakelock, with a maximum delay of 1 hour
        Log.d(tag, "acquiring wakelock");
        lock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                tag+" : screen_on");

        lock.acquire(MAX_TIME);

        REQUIRES_INIT = false;

        //use the runnable to start tile updates every second
        if(future==null) {
            ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
            future = executorService.scheduleWithFixedDelay(r,
                    0, 1000, TimeUnit.MILLISECONDS);
        }

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // release the acquired wakelock
        Log.d(tag, "called onDestroy");
        if(lock != null && lock.isHeld())
            Log.d(tag, "releasing wakelock");
        //lock.release();
    }
}
