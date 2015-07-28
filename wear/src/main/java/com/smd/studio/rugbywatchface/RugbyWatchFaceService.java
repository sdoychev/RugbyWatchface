package com.smd.studio.rugbywatchface;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.view.SurfaceHolder;

public class RugbyWatchFaceService extends CanvasWatchFaceService {

    //TODO Next step in tutorial - https://developer.android.com/training/wearables/watch-faces/drawing.html

    //TODO Change launcher icon
    //TODO? Change app name
    //TODO Change @xml/watch_face
    //TODO Change @drawable/preview_analog
    //TODO Change @drawable/preview_analog_circular

    @Override
    public WatchEngine onCreateEngine() {
        return new WatchEngine();
    }

    private class WatchEngine extends CanvasWatchFaceService.Engine {

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            /* TODO initialize the rugby watch face */
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            /* TODO get device features (burn-in, low-bit ambient) */
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            /* TODO the time changed */
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            /* TODO the wearable switched between modes */
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
             /* TODO the rugby watch face became visible or invisible */
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            super.onDraw(canvas, bounds);
            /* TODO draw the rugby watch face */
        }
    }

}
