package com.smd.studio.rugbywatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Hours;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class RugbyWatchFaceService extends CanvasWatchFaceService {

    @Override
    public WatchEngine onCreateEngine() {
        return new WatchEngine();
    }

    private class WatchEngine extends CanvasWatchFaceService.Engine {

        static final int MSG_UPDATE_TIME = 0;
        static final int INTERACTIVE_UPDATE_RATE_MS = 1000;
        //Time reference
        Calendar calendar;
        //Receiver to update the time zone
        final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        int currentMinutes;
        int currentHours;
        boolean drawHours;
        //Watch-specific technical variables
        boolean registeredTimeZoneReceiver = false;
        boolean lowBitAmbient;
        boolean burnInProtection;
        boolean squareWatch;
        int tapCode;
        //Rotations for when drawing the hands
        float minuteRotation;
        float hourRotation;
        //Paints used when drawing
        Paint handsPaint;
        Paint ballPaint;
        Paint cupPaint;
        Paint daysPaint;
        Paint hoursPaint;
        //Dates for calculations
        int daysLeft;
        int hoursLeft;
        Date rwcStartDate;
        DateTime currentDateTime;
        //Handler to update the time once a second in interactive mode
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
                            currentDateTime = new DateTime(new Date(calendar.getTimeInMillis()));
                        }
                        break;
                }
            }
        };
        String daysLeftString;
        String hoursLeftString;
        DateTime rwcStartDateTime;
        //Text sizes
        float daysLeftSize;
        float scaledDaysLeftText;
        float hoursLeftSize;
        float scaledHoursLeftText;
        //Bitmaps for resources
        Bitmap backgroundBitmap;
        Bitmap backgroundScaledBitmap;
        Bitmap hourHandBitmap;
        Bitmap hourHandScaledBitmap;
        Bitmap minuteHandBitmap;
        Bitmap minuteHandScaledBitmap;
        Bitmap ballBitmap;
        Bitmap ballScaledBitmap;
        Bitmap cupBitmap;
        Bitmap cupScaledBitmap;
        Bitmap cupHoursBitmap;
        Bitmap cupHoursScaledBitmap;
        Bitmap backgroundAmbientBitmap;
        Bitmap backgroundScaledAmbientBitmap;
        Bitmap hourHandAmbientBitmap;
        Bitmap hourHandScaledAmbientBitmap;
        Bitmap minuteHandAmbientBitmap;
        Bitmap minuteHandScaledAmbientBitmap;
        Bitmap ballAmbientBitmap;
        Bitmap ballScaledAmbientBitmap;
        //Floats for bitmaps positioning
        float ballBitmapLeft;
        float ballBitmapTop;
        float cupBitmapLeft;
        float cupBitmapTop;
        float textRemainingX;
        float textRemainingY;
        //Watch-specific dimensions variables
        private int watchWidth;
        private int watchHeight;
        private float scale;
        private float centerX;
        private float centerY;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            init();

            setWatchFaceStyle(new WatchFaceStyle.Builder(RugbyWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setAcceptsTapEvents(true)
                    .setShowSystemUiTime(false)
                    .setStatusBarGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL)
                    .setHotwordIndicatorGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL)
                    .setViewProtectionMode(WatchFaceStyle.PROTECT_STATUS_BAR | WatchFaceStyle.PROTECT_HOTWORD_INDICATOR)
                    .build());
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            if (!insets.isRound()) {
                squareWatch = true;
            }
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
                handsPaint.setAntiAlias(antiAlias);
            }
            invalidate();
            updateTimer();
        }

        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            super.onTapCommand(tapType, x, y, eventTime);

            if (tapCode == tapType) {
                //Switch between drawing days and hours left until RWC.
                drawHours = !drawHours;
            }
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
            super.onSurfaceChanged(holder, format, width, height);
            // Get width and height for this watch specifically.
            watchWidth = width;
            watchHeight = height;
            // Find the center. Ignore the window insets so that, on round watches with a "chin",
            // the watch face is centered on the entire screen, not just the usable portion.
            centerX = width / 2f;
            centerY = height / 2f;

            scale = ((float) watchWidth) / (float) backgroundBitmap.getWidth();
            backgroundScaledBitmap = scaleBitmap(backgroundBitmap, scale);
            hourHandScaledBitmap = scaleBitmap(hourHandBitmap, scale);
            minuteHandScaledBitmap = scaleBitmap(minuteHandBitmap, scale);
            ballScaledBitmap = scaleBitmap(ballBitmap, scale);
            cupScaledBitmap = scaleBitmap(cupBitmap, scale);
            cupHoursScaledBitmap = scaleBitmap(cupHoursBitmap, scale);
            backgroundScaledAmbientBitmap = scaleBitmap(backgroundAmbientBitmap, scale);
            hourHandScaledAmbientBitmap = scaleBitmap(hourHandAmbientBitmap, scale);
            minuteHandScaledAmbientBitmap = scaleBitmap(minuteHandAmbientBitmap, scale);
            ballScaledAmbientBitmap = scaleBitmap(ballAmbientBitmap, scale);

            ballBitmapLeft = centerX - ballScaledBitmap.getWidth() / 2;
            ballBitmapTop = centerY - ballScaledBitmap.getHeight() / 2;
            cupBitmapLeft = centerX - cupScaledBitmap.getWidth() / 2;
            cupBitmapTop = centerY + cupScaledBitmap.getHeight() / 2.5f;
            textRemainingX = centerX - cupScaledBitmap.getWidth() / 4.5f;
            textRemainingY = centerY + watchHeight / 4.25f;
            //Adjustments for watches with bottom black "chin"
            if (!squareWatch && (watchWidth != watchHeight)) {
                ballBitmapTop -= ballScaledBitmap.getHeight() / 3.5f;
                cupBitmapTop -= cupBitmap.getHeight() / 6;
                textRemainingY -= cupBitmap.getHeight() / 4;
            }

            daysLeftSize = getResources().getDimension(R.dimen.days_left_size);
            scaledDaysLeftText = daysLeftSize * scale;
            daysPaint.setTextSize(scaledDaysLeftText);
            hoursLeftSize = getResources().getDimension(R.dimen.hours_left_size);
            scaledHoursLeftText = hoursLeftSize * scale;
            hoursPaint.setTextSize(scaledHoursLeftText);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            // Update the time
            calendar.setTimeInMillis(System.currentTimeMillis());

            if (!isInAmbientMode()) {
                // Interactive mode
                // Draw the background first
                drawBackground(canvas, backgroundScaledBitmap);
                // Draw the cup and remaining days or hours
                daysLeft = Days.daysBetween(currentDateTime, rwcStartDateTime).getDays();
                hoursLeft = Hours.hoursBetween(currentDateTime, rwcStartDateTime).getHours();
                if (drawHours) {
                    drawCupAndHours(canvas, cupHoursScaledBitmap);
                } else {
                    drawCupAndDays(canvas, cupScaledBitmap);
                }
                // Draw the hands, minutes first, then hours
                drawHands(canvas, minuteHandScaledBitmap, hourHandScaledBitmap);
                // Draw rugby ball on top of hands
                drawBall(canvas, ballScaledBitmap);
            } else {
                // Ambient mode
                // Draw the background first
                drawBackground(canvas, backgroundScaledAmbientBitmap);
                // Draw the hands, minutes first, then hours
                drawHands(canvas, minuteHandScaledAmbientBitmap, hourHandScaledAmbientBitmap);
                // Draw rugby ball on top of hands
                drawBall(canvas, ballScaledAmbientBitmap);
            }
        }

        private void drawBackground(Canvas canvas, Bitmap backgroundBitmap) {
            canvas.drawBitmap(backgroundBitmap, 0, 0, null);
        }

        private void drawCupAndDays(Canvas canvas, Bitmap cupBitmap) {
            daysLeftString = "" + daysLeft;
            if (daysLeft < 10) {
                daysLeftString = " " + daysLeft;
            }
            canvas.drawBitmap(cupBitmap, cupBitmapLeft, cupBitmapTop, cupPaint);
            canvas.drawText(daysLeftString, textRemainingX, textRemainingY, daysPaint);
        }

        private void drawCupAndHours(Canvas canvas, Bitmap cupBitmap) {
            hoursLeftString = "" + hoursLeft;
            if (hoursLeft < 100) {

                scaledHoursLeftText = scaledDaysLeftText;
                hoursPaint.setTextSize(scaledHoursLeftText);

                if (hoursLeft < 10) {
                    hoursLeftString = " " + hoursLeft;
                }
            }
            canvas.drawBitmap(cupBitmap, cupBitmapLeft, cupBitmapTop, cupPaint);
            canvas.drawText(hoursLeftString, textRemainingX, textRemainingY, hoursPaint);
        }

        private void drawHands(Canvas canvas, Bitmap minuteHandBitmap, Bitmap hourHandBitmap) {
            canvas.save();
            currentMinutes = calendar.get(Calendar.MINUTE);
            currentHours = calendar.get(Calendar.HOUR);
            minuteRotation = currentMinutes * 6;
            hourRotation = ((currentHours + (currentMinutes / 60f)) * 30);
            canvas.rotate(minuteRotation, centerX, centerY);
            canvas.drawBitmap(minuteHandBitmap, centerX - minuteHandBitmap.getWidth() / 2f, centerY - minuteHandBitmap.getHeight(), handsPaint);
            canvas.rotate(360 - minuteRotation + hourRotation, centerX, centerY);
            canvas.drawBitmap(hourHandBitmap, centerX - hourHandBitmap.getWidth() / 2f, centerY - hourHandBitmap.getHeight(), handsPaint);
            canvas.restore();
        }

        private void drawBall(Canvas canvas, Bitmap ballBitmap) {
            canvas.drawBitmap(ballBitmap, ballBitmapLeft, ballBitmapTop, ballPaint);
        }

        private void updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
                currentDateTime = new DateTime(new Date(calendar.getTimeInMillis()));
            }
        }

        private void init() {
            calendar = Calendar.getInstance();
            currentDateTime = new DateTime(new Date(calendar.getTimeInMillis()));
            rwcStartDate = new Date(115, 8, 18, 21, 0); //The RWC start date is 18 September 2015 19:00 GMT
            rwcStartDateTime = new DateTime(rwcStartDate);
            daysLeft = Days.daysBetween(currentDateTime, rwcStartDateTime).getDays();
            hoursLeft = Hours.hoursBetween(currentDateTime, rwcStartDateTime).getHours();
            currentMinutes = 0;
            currentHours = 0;
            minuteRotation = 0f;
            hourRotation = 0f;
            watchWidth = -1;
            watchHeight = -1;
            tapCode = WatchFaceService.TAP_TYPE_TOUCH;
            Resources resources = RugbyWatchFaceService.this.getResources();
            Drawable backgroundDrawable = resources.getDrawable(R.drawable.background, null);
            if (backgroundDrawable != null) {
                backgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();
            }
            Drawable hourHandDrawable = resources.getDrawable(R.drawable.hour_hand, null);
            if (hourHandDrawable != null) {
                hourHandBitmap = ((BitmapDrawable) hourHandDrawable).getBitmap();
            }
            Drawable minuteHandDrawable = resources.getDrawable(R.drawable.minute_hand, null);
            if (minuteHandDrawable != null) {
                minuteHandBitmap = ((BitmapDrawable) minuteHandDrawable).getBitmap();
            }
            Drawable ballDrawable = resources.getDrawable(R.drawable.ball, null);
            if (ballDrawable != null) {
                ballBitmap = ((BitmapDrawable) ballDrawable).getBitmap();
            }
            Drawable cupDrawable = resources.getDrawable(R.drawable.cup, null);
            if (cupDrawable != null) {
                cupBitmap = ((BitmapDrawable) cupDrawable).getBitmap();
            }
            Drawable cupHoursDrawable = resources.getDrawable(R.drawable.cup_hours, null);
            if (cupHoursDrawable != null) {
                cupHoursBitmap = ((BitmapDrawable) cupHoursDrawable).getBitmap();
            }
            Drawable backgroundAmbientDrawable = resources.getDrawable(R.drawable.background_ambient, null);
            if (backgroundAmbientDrawable != null) {
                backgroundAmbientBitmap = ((BitmapDrawable) backgroundAmbientDrawable).getBitmap();
            }
            Drawable hourHandAmbientDrawable = resources.getDrawable(R.drawable.hour_hand_ambient, null);
            if (hourHandAmbientDrawable != null) {
                hourHandAmbientBitmap = ((BitmapDrawable) hourHandAmbientDrawable).getBitmap();
            }
            Drawable minuteHandAmbientDrawable = resources.getDrawable(R.drawable.minute_hand_ambient, null);
            if (minuteHandAmbientDrawable != null) {
                minuteHandAmbientBitmap = ((BitmapDrawable) minuteHandAmbientDrawable).getBitmap();
            }
            Drawable ballAmbientDrawable = resources.getDrawable(R.drawable.ball_ambient, null);
            if (ballAmbientDrawable != null) {
                ballAmbientBitmap = ((BitmapDrawable) ballAmbientDrawable).getBitmap();
            }
            handsPaint = new Paint();
            handsPaint.setAntiAlias(true);
            handsPaint.setFilterBitmap(true);
            ballPaint = new Paint();
            ballPaint.setShadowLayer(1.0f, 2.0f, 2.0f, Color.BLACK);
            ballPaint.setFilterBitmap(true);
            cupPaint = new Paint();
            cupPaint.setFilterBitmap(true);
            daysPaint = new Paint();
            daysPaint.setColor(Color.WHITE);
            daysPaint.setAntiAlias(true);
            hoursPaint = new Paint();
            hoursPaint.setColor(Color.WHITE);
            hoursPaint.setAntiAlias(true);
        }

        private Bitmap scaleBitmap(Bitmap bitmap, float scale) {
            int width = (int) ((float) bitmap.getWidth() * scale);
            int height = (int) ((float) bitmap.getHeight() * scale);
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                return Bitmap.createScaledBitmap(bitmap, width, height, true /* filter */);
            } else {
                return bitmap;
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
