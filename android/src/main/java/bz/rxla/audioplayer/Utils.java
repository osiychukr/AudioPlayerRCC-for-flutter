package bz.rxla.audioplayer;

import android.content.Context;
import android.util.TypedValue;

public final class Utils {

    public static float toDp(int i, Context context) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) i, context.getResources().getDisplayMetrics());
    }
}
