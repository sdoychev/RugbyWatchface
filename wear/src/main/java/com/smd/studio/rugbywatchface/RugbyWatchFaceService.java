package com.smd.studio.rugbywatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;

import java.util.Calendar;
import java.util.TimeZone;

public class RugbyWatchFaceService extends CanvasWatchFaceService {

    //TODO Next step in tutorial - https://developer.android.com/training/wearables/watch-faces/information.html

    //TODO Change launcher icon
    //TODO? Change app name
    //TODO Change @drawable/preview_analog
    //TODO Change @drawable/preview_analog_circular

    @Override
    public WatchEngine onCreateEngine() {
        return new WatchEngine();
    }

    private class WatchEngine extends CanvasWatchFaceService.Engine {

        static final int MSG_UPDATE_TIME = 0;
        static final int INTERACTIVE_UPDATE_RATE_MS = 1000;
        // handler to update the time once a second in interactive mode
        //TODO memory leak?
        final Handler updateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };
        Calendar calendar;
        // receiver to update the time zone
        final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        boolean registeredTimeZoneReceiver = false;
        boolean lowBitAmbient;
        boolean burnInProtection;
        Bitmap backgroundBitmap;
        Bitmap backgroundScaledBitmap;
        Paint backgroundPaint;
        Paint hourPaint;
        Paint minutePaint;
        Paint secondPaint;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            // TODO Adjust the system UI to own needs
            setWatchFaceStyle(new WatchFaceStyle.Builder(RugbyWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = RugbyWatchFaceService.this.getResources();
            //TODO Change backgroundPaint with a backgroundDrawable
            //Drawable backgroundDrawable = resources.getDrawable(R.drawable.bg, null);
            //backgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();
            backgroundPaint.setColor(resources.getColor(R.color.background));

            hourPaint = new Paint();
            hourPaint.setColor(resources.getColor(R.color.hands));
            hourPaint.setStrokeWidth(resources.getDimension(R.dimen.hand_stroke));
            hourPaint.setAntiAlias(true);
            hourPaint.setStrokeCap(Paint.Cap.ROUND);

            calendar = Calendar.getInstance();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();

            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (lowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                hourPaint.setAntiAlias(antiAlias);
                minutePaint.setAntiAlias(antiAlias);
                secondPaint.setAntiAlias(antiAlias);
            }
            invalidate();
            updateTimer();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                calendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();
            }
            // Whether the timer should be running depends on whether we're visible and whether we're in ambient mode, so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (backgroundScaledBitmap == null || backgroundScaledBitmap.getWidth() != width || backgroundScaledBitmap.getHeight() != height) {
                backgroundScaledBitmap = Bitmap.createScaledBitmap(backgroundBitmap, width, height, true /* filter */);
            }
            super.onSurfaceChanged(holder, format, width, height);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Update the time
            calendar.setTimeInMillis(System.currentTimeMillis());

            // Constant to help calculate clock hand rotations
            final float TWO_PI = (float) Math.PI * 2f;

            int width = bounds.width();
            int height = bounds.height();

            canvas.drawBitmap(backgroundScaledBitmap, 0, 0, null);

            // Find the center. Ignore the window insets so that, on round watches with a "chin",
            // the watch face is centered on the entire screen, not just the usable portion.
            float centerX = width / 2f;
            float centerY = height / 2f;

            // Compute rotations and lengths for the clock hands.
            float seconds = calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1000f;
            float secRot = seconds / 60f * TWO_PI;
            float minutes = calendar.get(Calendar.MINUTE) + seconds / 60f;
            float minRot = minutes / 60f * TWO_PI;
            float hours = calendar.get(Calendar.HOUR) + minutes / 60f;
            float hrRot = hours / 12f * TWO_PI;

            //TODO check the hardcoded values 20, 40 and 80
            float secLength = centerX - 20;
            float minLength = centerX - 40;
            float hrLength = centerX - 80;

            // Only draw the second hand in interactive mode.
            if (!isInAmbientMode()) {
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, secondPaint);
            }

            // Draw the minute and hour hands.
            float minX = (float) Math.sin(minRot) * minLength;
            float minY = (float) -Math.cos(minRot) * minLength;
            canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, minutePaint);
            float hrX = (float) Math.sin(hrRot) * hrLength;
            float hrY = (float) -Math.cos(hrRot) * hrLength;
            canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, hourPaint);
        }

        private void updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        private void registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            RugbyWatchFaceService.this.registerReceiver(timeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = false;
            RugbyWatchFaceService.this.unregisterReceiver(timeZoneReceiver);
        }
    }

}
