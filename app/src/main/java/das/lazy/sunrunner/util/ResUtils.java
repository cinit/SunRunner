package das.lazy.sunrunner.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.view.ViewCompat;
import das.lazy.sunrunner.R;

public class ResUtils {


    public static LinearLayout newDialogClickableItemClickToCopy(final Context ctx, String title, String value, ViewGroup vg, boolean attach) {
        return newDialogClickableItem(ctx, title, value, new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Context c = v.getContext();
                String msg = ((TextView) v).getText().toString();
                if (msg.length() > 0) {
                    ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        //cm.setText(msg);
                        cm.setPrimaryClip(ClipData.newPlainText(null, msg));
                        Toast.makeText(ctx, "已复制文本", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }
        }, vg, attach);
    }

    public static LinearLayout newDialogClickableItem(final Context ctx, String title, String value, View.OnLongClickListener ll, ViewGroup vg, boolean attach) {
        LinearLayout root = (LinearLayout) LayoutInflater.from(ctx).inflate(R.layout.dialog_clickable_item, vg, false);
        TextView t = root.findViewById(R.id.dialogClickableItemTitle);
        TextView v = root.findViewById(R.id.dialogClickableItemValue);
        t.setText(title);
        v.setText(value);
        if (ll != null) {
            v.setOnLongClickListener(ll);
            //v.setBackgroundDrawable(ResUtils.getDialogClickableItemBackground());
            ViewCompat.setBackground(v, getDialogClickableItemBackground());
        }
        if (attach) {
            vg.addView(root);
        }
        return root;
    }

    public static StateListDrawable getDialogClickableItemBackground() {
        StateListDrawable sd = new StateListDrawable();
        sd.addState(new int[]{android.R.attr.state_pressed}, new HcbBackgroundDrawable(0x40808080));
        sd.addState(new int[]{}, new DummyDrawable());
        return sd;
    }

}
