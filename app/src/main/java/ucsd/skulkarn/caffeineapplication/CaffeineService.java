package ucsd.skulkarn.caffeineapplication;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.*;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class CaffeineService extends TileService {

    private static final int MAX_TIME = 3600000;

    private static final int TIMES[] = {
            30000, // 30 seconds -- TEST
            300000, // 5 minutes
            900000, // 15 minutes
            1800000, // 30 minutes
            3600000 // 1 hour
    };

    int index = -1;

    private static final String tag = "CaffeineService";
    public static final String timeTag = "TIME";
    private WakeLock lock;
    private Timer timer;
    private Handler updateTile = new Handler();

    Runnable r = new Runnable() {
        @Override
        public void run() {
            //update the tile every second
            Log.d(tag, "scheduled at fixed rate, delay = " + delay);
            Tile tile = getQsTile();
            tile.setLabel(String.format(Locale.US,"%02d:%02d",
                    delay/60000, ((delay - delay/60000*60000)/1000)));
            delay = delay - 1000;
            tile.updateTile();

            //keep looping
            if(delay > 0)
                updateTile.postDelayed(this, 1000);
            //stop looping
            else {
                // display a completion message to the user
                tile.setLabel("Caffination complete!");
                tile.updateTile();
                // reset index so user can start caffinating again
                index = -1;
                //release the wakelock and stop all callbacks.
                Log.d(tag, "releasing wakelock manually");
                lock.release();
                //updateTile.removeCallbacks(this);
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
        Tile tile = getQsTile();
        tile.setLabel("Start Caffinating!");
        tile.setState(Tile.STATE_ACTIVE);
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        delay = index >= TIMES.length - 1 ? TIMES[(index=0)] : TIMES[++index];
        Log.d(tag, "setting delay to " + delay);

        //start main caffeineservice by intent
        Intent intent = new Intent(this, CaffeineService.class);
        intent.putExtra(timeTag, delay);
        Log.d(tag, "starting CaffeineService by Intent");
        startService(intent);

        //use the runnable to start every-second tile updates
        updateTile.removeCallbacks(r);
        updateTile.postDelayed(r, 1000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // call the parent method
        super.onStartCommand(intent, flags, startId);

        // obtain power manager to create wakelock
        PowerManager pm = ((PowerManager) getSystemService(Context.POWER_SERVICE));

        // check if the lock is already held.
        if(lock != null && lock.isHeld()){
            Log.d(tag, "releasing old wakelock");
            //release the lock AND clear the timer if changing the amount of time
            lock.release();
            timer.cancel();
        }

        //acquire the wakelock, with a maximum delay of 1 hour
        Log.d(tag, "acquiring wakelock");
        lock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, tag+" : screen_on");
        lock.acquire(MAX_TIME);

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // release the acquired wakelock
        Log.d(tag, "releasing wakelock");
        if(lock != null && lock.isHeld())
            lock.release();
    }
}
