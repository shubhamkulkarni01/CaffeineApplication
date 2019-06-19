package ucsd.skulkarn.caffeineapplication;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.content.Intent;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void click(View v){
        // tester to try the service. uses a time value of 30 seconds (30000 milliseconds).
        Intent service = new Intent(this, CaffeineService.class);
        service.putExtra(CaffeineService.timeTag, 30000);
        startService(service);
    }
}
