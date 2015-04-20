package cn.spade.android.eventbus.demo;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import de.greenrobot.event.EventBus;
import de.greenrobot.event.EventBusBuilder;


public class EventBusActivity extends ActionBarActivity {


  EventBus eventBus;
  @Override
  protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_event_bus);

//    eventBus = EventBus.builder().build();
    eventBus = EventBus.builder().skipMethodVerificationFor(this.getClass()).build();
  }

  @Override
  protected void onResume() {
	super.onResume();
    eventBus.register(this);
  }

  @Override
  protected void onPause() {
	super.onPause();
    eventBus.unregister(this);
  }

  public void onEvent(Boolean b){

  }

  public void onEvent(Intent intent){

  }

//  public void onEventTest(Intent intent){
//
//  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
	// Inflate the menu; this adds items to the action bar if it is present.
	getMenuInflater().inflate(R.menu.menu_event_bus, menu);
	return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
	// Handle action bar item clicks here. The action bar will
	// automatically handle clicks on the Home/Up button, so long
	// as you specify a parent activity in AndroidManifest.xml.
	int id = item.getItemId();

	//noinspection SimplifiableIfStatement
	if (id == R.id.action_settings) {
	  return true;
	}

	return super.onOptionsItemSelected(item);
  }
}
