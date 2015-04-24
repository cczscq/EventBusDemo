package cn.spade.android.eventbus.demo;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import butterknife.ButterKnife;
import butterknife.OnClick;
import cn.spade.android.eventbus.demo.model.Event;
import cn.spade.android.eventbus.demo.model.EventPull;
import cn.spade.android.eventbus.demo.model.EventPush;
import cn.spade.android.eventbus.demo.tool.LogTool;
import de.greenrobot.event.EventBus;


public class EventBusActivity extends ActionBarActivity {

  private static final String TAG = EventBus.class.getSimpleName();

  private EventBus eventBus;
  @Override
  protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_event_bus);
    ButterKnife.inject(this);

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

  @OnClick({R.id.post_event_default, R.id.post_event_pull, R.id.post_event_push})
  public void onClick(View view){
    switch (view.getId()){
      case R.id.post_event_default:
        eventBus.post(new Event("default"));
        break;
      case R.id.post_event_pull:
        eventBus.post(new EventPull("pull"));
        break;
      case R.id.post_event_push:
        eventBus.post(new EventPush("push"));
        break;
    }
  }

  //onEvent
  //onEventMainThread
  //onEventBackgroundThread
  //onEventAsync


  public void onEventMainThread(Event event){
    LogTool.info(TAG, "onEvent(): event = " + event.toString());
  }

  public void onEventMainThread(EventPull eventPull){
    LogTool.info(TAG, "onEvent(): eventPull = " + eventPull.toString());
  }

  public void onEventMainThread(EventPush eventPush){
    LogTool.info(TAG, "onEvent(): eventPush = " + eventPush.toString());
  }

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
