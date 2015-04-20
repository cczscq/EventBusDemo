package cn.spade.android.eventbus.demo;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import butterknife.ButterKnife;
import butterknife.OnClick;
import cn.spade.android.eventbus.demo.model.ActionGit;
import cn.spade.android.eventbus.demo.model.ActionSvn;
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

  @OnClick({R.id.post_event_boolean, R.id.post_event_action_git, R.id.post_event_action_svn})
  public void onClick(View view){
    switch (view.getId()){
      case R.id.post_event_boolean:
        eventBus.post(new Boolean(true));
        break;
      case R.id.post_event_action_git:
        eventBus.post(new ActionGit("git"));
        break;
      case R.id.post_event_action_svn:
        eventBus.post(new ActionSvn("svn"));
        break;
    }
  }

  public void onEvent(Boolean b){
    LogTool.info(TAG, "onEvent(): Boolean = " + b);
  }

  public void onEvent(ActionGit actionGit){
    LogTool.info(TAG, "onEvent(): ActionGit = " + actionGit.toString());
  }

  public void onEvent(ActionSvn actionSvn){
    LogTool.info(TAG, "onEvent(): ActionSvn = " + actionSvn.toString());
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
