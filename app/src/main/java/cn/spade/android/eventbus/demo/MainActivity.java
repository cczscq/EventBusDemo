package cn.spade.android.eventbus.demo;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import butterknife.ButterKnife;
import butterknife.OnClick;
import de.greenrobot.event.EventBus;


public class MainActivity extends ActionBarActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_main);
    ButterKnife.inject(this);
  }

  @OnClick({R.id.startEventBus})
  public void onClick(View view){
    switch (view.getId()){
      case R.id.startEventBus:
        Intent intent = new Intent(MainActivity.this, EventBusActivity.class);
        startActivity(intent);
        break;
    }
  }
}
