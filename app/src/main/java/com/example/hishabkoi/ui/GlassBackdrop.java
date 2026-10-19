package com.example.hishabkoi.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;

import com.example.hishabkoi.R;

/**
 * A few large, heavily blurred brand-blue blobs, drawn behind every screen.
 * The translucent panels on top let them show through softly, which gives the frosted-glass look.
 * Android has no backdrop blur for views, so the blur lives here instead of in the panels.
 */
public class GlassBackdrop extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GlassBackdrop(Context context) {
        super(context);
        paint.setColor(context.getColor(R.color.backdrop_blue));
        float blur = 70 * getResources().getDisplayMetrics().density;
        setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Puts the backdrop behind the activity's layout. Call after setContentView. */
    public static void attach(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0
                || content.getChildAt(0) instanceof GlassBackdrop) return;
        content.addView(new GlassBackdrop(activity), 0,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        canvas.drawCircle(w * 0.95f, h * 0.10f, w * 0.55f, paint);
        canvas.drawCircle(w * 0.05f, h * 0.48f, w * 0.50f, paint);
        canvas.drawCircle(w * 0.90f, h * 0.82f, w * 0.48f, paint);
    }
}
