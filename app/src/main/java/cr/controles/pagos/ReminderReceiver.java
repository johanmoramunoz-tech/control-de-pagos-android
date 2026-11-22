package cr.controles.pagos;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class ReminderReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){
  if(ReminderScheduler.ACTION.equals(i.getAction())){
   ReminderScheduler.showIfPending(c,i);
  }
  ReminderScheduler.refresh(c);
 }
}
