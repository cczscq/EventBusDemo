package cn.spade.android.eventbus.demo.model;

/**
 * Created by zhaosc on 4/20/15.
 */
public class ActionSvn {

  private String name;

  public ActionSvn(String name){
	this.name = name;
  }

  public String getName() {
	return name;
  }

  public void setName(String name) {
	this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }
}
