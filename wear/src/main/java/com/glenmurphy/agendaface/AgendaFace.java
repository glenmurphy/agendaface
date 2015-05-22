package com.glenmurphy.agendaface;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.support.wearable.provider.WearableCalendarContract;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextPaint;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;

import android.provider.CalendarContract;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import android.graphics.Typeface;
import android.graphics.RectF;
import android.view.WindowInsets;
/**
 * Proof of concept sample watch face that demonstrates how a watch face can load calendar data.
 */
public class AgendaFace extends CanvasWatchFaceService {
  private static final String TAG = "AgendaFace";

  @Override
  public Engine onCreateEngine() {
    return new Engine();
  }

  private class Engine extends CanvasWatchFaceService.Engine {

    static final int BACKGROUND_COLOR = Color.BLACK;

    static final int TIME_COLOR = Color.WHITE;
    static final int TIME_TEXT_SIZE = 110;
    static final int RECT_TIME_POSITION = 57; // Center
    Typeface TIME_TYPEFACE =
       Typeface.createFromAsset(getAssets(), "VisitorTT1BRK.ttf");

    static final int DATE_TEXT_COLOR = Color.GRAY;
    static final int DATE_TEXT_SIZE = 21;

    // stop showing current meeting after this amount of time since it started.
    static final int EVENT_START_CUTOFF = 60000 * 20;

    static final int HEADER_TEXT_COLOR = Color.WHITE;
    static final int HEADER_TEXT_SIZE = 22;//24;
    static final int HEADER_TITLE_INDENT = 64;//72;

    static final int DETAIL_TEXT_COLOR = Color.GRAY;
    static final int DETAIL_TEXT_SIZE = 22;//24;
    static final int DETAIL_TEXT_PADDING = 24;

    static final int EVENT_LINE_BACKGROUND_WIDTH = 18;
    static final int EVENT_LINE_BACKGROUND_BORDER_WIDTH = 2;
    // How much to inset from inner edge of background
    static final int EVENT_LINE_INSET = 6;
    static final int EVENT_LINE_WIDTH = 6;
    // How much to inset arcs (by degrees) to counter line cap rounding protrusion
    static final float EVENT_LINE_ARC_OFFSET = 1.5f;
    static final int EVENT_LINES_CUTOFF = 60000 * 60 * 12; // only show lines for the next N

    static final int MSG_LOAD_MEETINGS = 0;

    /** Paint used to draw text. */
    final TextPaint mDateTextPaint = new TextPaint();
    final TextPaint mTimeTextPaint = new TextPaint();
    final TextPaint mHeaderTextPaint = new TextPaint();
    final TextPaint mDetailTextPaint = new TextPaint();
    final Paint mEventLinePaint = new Paint();
    final Paint mEventBackgroundPaint = new Paint();
    final Paint mEventBackgroundBorder = new Paint();
    final Paint mNowLinePaint = new Paint();

    final DateFormat mFormatTime = new SimpleDateFormat("HH:mm");
    final SimpleDateFormat mFormatDate = new SimpleDateFormat("MMM d");

    List<CalendarEvent> events = new ArrayList<>();

    private AsyncTask<Void, Void, Boolean> mLoadMeetingsTask;

    private Boolean mPropLowBitAmbient = false;
    private Boolean mIsRound = false;
    private float mEventLineAngleOffset = 0;

    /** Handler to load the meetings once a minute in interactive mode. */
    final Handler mLoadMeetingsHandler = new Handler() {
      @Override
      public void handleMessage(Message message) {
        switch (message.what) {
          case MSG_LOAD_MEETINGS:
            cancelLoadMeetingTask();
            mLoadMeetingsTask = new LoadMeetingsTask();
            mLoadMeetingsTask.execute();
            break;
        }
      }
    };

    private boolean mIsReceiverRegistered;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_PROVIDER_CHANGED.equals(intent.getAction())
           && WearableCalendarContract.CONTENT_URI.equals(intent.getData())) {
          cancelLoadMeetingTask();
          mLoadMeetingsHandler.sendEmptyMessage(MSG_LOAD_MEETINGS);
        }
      }
    };

    @Override
    public void onCreate(SurfaceHolder holder) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "onCreate");
      }
      super.onCreate(holder);
      setWatchFaceStyle(new WatchFaceStyle.Builder(AgendaFace.this)
         .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
         .setPeekOpacityMode(WatchFaceStyle.PEEK_OPACITY_MODE_OPAQUE)
         .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
         .setShowSystemUiTime(false)
         .setStatusBarGravity(Gravity.TOP | Gravity.CENTER)
         .build());
      // Text AA is set dynamically in setTextLowBitMode();
      mDateTextPaint.setColor(DATE_TEXT_COLOR);
      mDateTextPaint.setTextSize(DATE_TEXT_SIZE);
      mDateTextPaint.setShadowLayer(6, 0, 2, Color.BLACK);

      mTimeTextPaint.setColor(TIME_COLOR);
      mTimeTextPaint.setTextSize(TIME_TEXT_SIZE);
      mTimeTextPaint.setTypeface(TIME_TYPEFACE);
      mTimeTextPaint.setShadowLayer(18, 0, 7, Color.BLACK);
      mTimeTextPaint.setTypeface(TIME_TYPEFACE);

      mHeaderTextPaint.setColor(HEADER_TEXT_COLOR);
      mHeaderTextPaint.setTextSize(HEADER_TEXT_SIZE);

      mDetailTextPaint.setColor(DETAIL_TEXT_COLOR);
      mDetailTextPaint.setTextSize(DETAIL_TEXT_SIZE);

      mEventLinePaint.setStyle(Paint.Style.STROKE);
      mEventLinePaint.setAntiAlias(true);
      mEventLinePaint.setStrokeCap(Paint.Cap.ROUND);
      mEventLinePaint.setStrokeWidth(EVENT_LINE_WIDTH);
      mEventLinePaint.setShadowLayer(5, 0, 1, Color.BLACK);

      mNowLinePaint.setStyle(Paint.Style.STROKE);
      mNowLinePaint.setAntiAlias(true);
      mNowLinePaint.setStrokeCap(Paint.Cap.ROUND);
      mNowLinePaint.setStrokeWidth(EVENT_LINE_WIDTH);
      mNowLinePaint.setColor(Color.RED);

      mEventBackgroundPaint.setStyle(Paint.Style.STROKE);
      mEventBackgroundPaint.setAntiAlias(true);
      mEventBackgroundPaint.setStrokeWidth(EVENT_LINE_BACKGROUND_WIDTH);
      mEventBackgroundPaint.setColor(Color.argb(255, 11, 12, 12));
      mEventBackgroundPaint.setShadowLayer(16, 0, 6, Color.BLACK);

      mEventBackgroundBorder.setStyle(Paint.Style.STROKE);
      mEventBackgroundBorder.setAntiAlias(true);
      mEventBackgroundBorder.setStrokeWidth(EVENT_LINE_BACKGROUND_BORDER_WIDTH);
      mEventBackgroundBorder.setColor(Color.argb(255, 24, 24, 24));

      mLoadMeetingsHandler.sendEmptyMessage(MSG_LOAD_MEETINGS);
    }

    @Override
    public void onPropertiesChanged(Bundle properties) {
      super.onPropertiesChanged(properties);
      mPropLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
    }

    @Override
    public void onDestroy() {
      mLoadMeetingsHandler.removeMessages(MSG_LOAD_MEETINGS);
      cancelLoadMeetingTask();
      super.onDestroy();
    }

    @Override
    public void onPeekCardPositionUpdate(Rect r) {
      super.onPeekCardPositionUpdate(r);
      invalidate();
    }

    @Override
    public void onAmbientModeChanged(boolean isAmbient) {
      super.onAmbientModeChanged(isAmbient);

      // Need to update backgrounds behind any peek cards.
      invalidate();
    }

    @Override
    public void onApplyWindowInsets(WindowInsets insets) {
      Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
      super.onApplyWindowInsets(insets);
      mIsRound = insets.isRound();
    }

    @Override
    public void onTimeTick() {
      super.onTimeTick();
      invalidate();
    }

    private void setTextLowBitMode() {
      Boolean inLowBitAmbient = (mPropLowBitAmbient && isInAmbientMode());
      mDateTextPaint.setAntiAlias(!inLowBitAmbient);
      mTimeTextPaint.setAntiAlias(!inLowBitAmbient);
      mHeaderTextPaint.setAntiAlias(!inLowBitAmbient);
      mDetailTextPaint.setAntiAlias(!inLowBitAmbient);

      // Add OLED-saving measures.
      if (isInAmbientMode() && !mPropLowBitAmbient) {
        mTimeTextPaint.setStrokeWidth(2f);
        mTimeTextPaint.setStyle(Paint.Style.STROKE);
      } else {
        mTimeTextPaint.setStrokeWidth(0);
        mTimeTextPaint.setStyle(Paint.Style.FILL);
      }
    }

    private List<CalendarEvent> getNextEvents(int numEvents, Boolean startCutoff) {
      Date now = new Date();

      List<CalendarEvent> filtered = new ArrayList<>();

      int index = 0;
      for (CalendarEvent event : events) {
        // Remember there's a different set of filters in the LoadMeetingsTask that stops some
        // things ever getting into the events list.

        // Don't show events that started more than 20 minutes ago.
        if (startCutoff) {
          // Draw events.
          Date cutoff = new Date(now.getTime() - (EVENT_START_CUTOFF));
          if (event.getStart().compareTo(cutoff) < 0)
            continue;
        }

        // Don't show events that have finished - normally the calendar API flushes these,
        // but that doesn't refresh as often as we paint.
        if (event.getEnd().compareTo(now) < 0) continue;

        filtered.add(event);

        index++;
        if (index >= numEvents)
          break;
      }

      return filtered;
    }

    private List<CalendarEvent> getNextEvents(Boolean startCutoff) {
      return getNextEvents(events.size(), startCutoff);
    }

    private void drawEvent(Canvas canvas, CalendarEvent event, float x, float y) {
      // Draw time
      Date start = event.getStart();
      String hourString = mFormatTime.format(start);
      canvas.drawText(hourString, x, y, mHeaderTextPaint);

      // Draw event title
      float hourWidth = mHeaderTextPaint.measureText(hourString);
      float pad = Math.max(hourWidth + 5, HEADER_TITLE_INDENT);
      canvas.drawText(":", x + hourWidth, y, mDetailTextPaint);
      canvas.drawText(event.getTitle().toUpperCase(), x + pad, y, mHeaderTextPaint);

      // Draw detail
      String location = event.getLocation();
      if (location.compareTo("") == 0)
        location = "no location";
      canvas.drawText(location, x, y + DETAIL_TEXT_PADDING, mDetailTextPaint);
    }

    private void drawEvents(Canvas canvas, float[] x, float[] y, int numEvents) {
      List<CalendarEvent> filtered = getNextEvents(numEvents, true);
      int index = 0;
      for (CalendarEvent event : filtered) {
        drawEvent(canvas, event, x[index], y[index]);
        index++;
      }
    }

    private void drawPeekCardBackground(Canvas canvas, Rect bounds) {
      // No need to draw this in regular mode - this is all abound doing the right thing
      // in ambient mode (where the cards go transparent, either because of our setting, or
      // low bit).
      if (!isInAmbientMode()) return;

      Rect peekCardPosition = getPeekCardPosition();
      if (!peekCardPosition.isEmpty()) {
        Paint shield = new Paint();

        // For cards that go off the screen, avoid drawing the bottom edge.
        //
        // Also, in ambient mode, the POLED jiggle can cause the left and right edges
        // to be shown on round devices, so in those cases, we set the bounds off those
        // edges too (bit daft, really).
        int offscreenOffset = 5;
        if (peekCardPosition.bottom > bounds.height() - 1) {
          peekCardPosition.set(
             mIsRound ? bounds.left - offscreenOffset : peekCardPosition.left,
             peekCardPosition.top,
             mIsRound ? bounds.right + offscreenOffset : peekCardPosition.right,
             peekCardPosition.bottom + offscreenOffset
          );
        }

        shield.setColor(Color.BLACK);
        canvas.drawRect(peekCardPosition, shield);

        // Draw white outline
        shield.setStrokeWidth(2);
        shield.setColor(Color.WHITE);
        shield.setStyle(Paint.Style.STROKE);
        canvas.drawRect(peekCardPosition, shield);
      }
    }

    /**
     * @param date the specified time
     * @return the angle (in degrees) for the specified time.
     */
    private int getClockAngleForTime(Date date) {
      // TODO: could probably convert date to Time instead.
      GregorianCalendar cal = new GregorianCalendar();
      cal.setTimeInMillis(date.getTime());
      return cal.get(Calendar.HOUR_OF_DAY) * 30 + cal.get(Calendar.MINUTE) / 2 - 90;
    }

    private void drawEventLine(Canvas canvas, RectF oval, CalendarEvent event, Boolean isNext) {
      float startDegrees = getClockAngleForTime(event.getStart());
      float endDegrees = getClockAngleForTime(event.getEnd());
      while(endDegrees < startDegrees)
        endDegrees += 360;

      startDegrees += EVENT_LINE_ARC_OFFSET;
      endDegrees -= EVENT_LINE_ARC_OFFSET;

      if (endDegrees < startDegrees)
        endDegrees = startDegrees + 1;

      Path myPath = new Path();
      myPath.arcTo(oval, startDegrees, endDegrees - startDegrees, true);

      mEventLinePaint.setColor(isNext ? Color.WHITE : Color.argb(255, 128, 128, 128));
      canvas.drawPath(myPath, mEventLinePaint);
    }

    private void drawEdgeEvents(Canvas canvas, RectF oval) {
      Date ignoreAfter = new Date(new Date().getTime() + (EVENT_LINES_CUTOFF));
      List<CalendarEvent> nextEvents = getNextEvents(false);
      int index = 0;
      for (CalendarEvent event : nextEvents) {
        // Stop processing if start time is greater than EVENT_LINES_CUTOFF (usually 12 hours)
        if (event.getStart().compareTo(ignoreAfter) > 0) break;

        drawEventLine(canvas, oval, event, (index == 0));
        index++;
      }
    }

    private void drawNowLine(Canvas canvas, RectF oval) {
      Path myPath = new Path();
      myPath.arcTo(oval, getClockAngleForTime(new Date()), 1, true);
      canvas.drawPath(myPath, mNowLinePaint);
    }

    private RectF getEdgeOval(Rect bounds) {
      float radius = bounds.width() / 2 - EVENT_LINE_BACKGROUND_WIDTH + EVENT_LINE_INSET;

      RectF oval = new RectF();
      oval.set(bounds.centerX() - radius, bounds.centerY() - radius,
               bounds.centerX() + radius, bounds.centerY() + radius);
      return oval;
    }

    private void drawRectLayout(Canvas canvas, Rect bounds) {
      Date now = new Date();

      // Draw the date
      String dateString = mFormatDate.format(now);
      float dateWidth = mDateTextPaint.measureText(dateString);
      // bounds.width() - dateWidth - 5
      canvas.drawText(dateString, 5, 22, mDateTextPaint);

      // Draw the events
      float[] x = new float[] {5, 5, 5, 5};
      float[] y = new float[] {117, 173, 229, 285};
      drawEvents(canvas, x, y, 4);

      // Draw the time
      String timeString = mFormatTime.format(now);
      //float timeWidth = mTimeTextPaint.measureText(timeString);
      //float timeX = bounds.width() / 2 - timeWidth / 2 + 5;
      float timeY = RECT_TIME_POSITION - ((mTimeTextPaint.ascent() + mTimeTextPaint.descent()) / 2);

      canvas.drawText(timeString, 7, timeY, mTimeTextPaint);
    }

    private void drawRoundLayout(Canvas canvas, Rect bounds) {
      Date now = new Date();
      RectF oval = getEdgeOval(bounds);

      // Describe next three events.
      float[] x = new float[] {60, 60, 60};
      float[] y = new float[] {70, 200, 255};
      drawEvents(canvas, x, y, 3);

      // Draw background
      float radius = oval.width() / 2 + EVENT_LINE_INSET; // compensate for inset in oval creation
      canvas.drawCircle(oval.centerX(), oval.centerY(), radius, mEventBackgroundPaint);
      canvas.drawCircle(oval.centerX(), oval.centerY(), radius - mEventBackgroundPaint.getStrokeWidth() / 2, mEventBackgroundBorder);

      // Draw events along edge.
      drawEdgeEvents(canvas, oval);

      // Draw the date
      String dateString = mFormatDate.format(now);
      float dateWidth = mDateTextPaint.measureText(dateString);
      canvas.drawText(dateString, bounds.width() / 2 - dateWidth / 2, 22, mDateTextPaint);

      // Draw the time
      String timeString = mFormatTime.format(now);
      float timeWidth = mTimeTextPaint.measureText(timeString);
      float timeY = bounds.height() / 2 - ((mTimeTextPaint.ascent() +
                    mTimeTextPaint.descent()) / 2) - 20; // 20 is just the offset we chose
      canvas.drawText(timeString, bounds.width() / 2 - timeWidth / 2 + 5, timeY, mTimeTextPaint);

      // Draw the current time.
      drawNowLine(canvas, oval);
    }

    @Override
    public void onDraw(Canvas canvas, Rect bounds) {
      Log.d(TAG, "Drawing");
      setTextLowBitMode();
      canvas.drawColor(BACKGROUND_COLOR);

      if (mIsRound)
        drawRoundLayout(canvas, bounds);
      else
        drawRectLayout(canvas, bounds);

      // Draw black behind any peek cards.
      drawPeekCardBackground(canvas, bounds);
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
      Log.d(TAG, "Visibility changed");
      super.onVisibilityChanged(visible);

      if (visible) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PROVIDER_CHANGED);
        filter.addDataScheme("content");
        filter.addDataAuthority(WearableCalendarContract.AUTHORITY, null);
        registerReceiver(mBroadcastReceiver, filter);
        mIsReceiverRegistered = true;

        mLoadMeetingsHandler.sendEmptyMessage(MSG_LOAD_MEETINGS);
      } else {
        if (mIsReceiverRegistered) {
          unregisterReceiver(mBroadcastReceiver);
          mIsReceiverRegistered = false;
        }
        mLoadMeetingsHandler.removeMessages(MSG_LOAD_MEETINGS);
      }
    }

    private void onMeetingsLoaded(Boolean changed) {
      if (changed) {
        Log.d(TAG, "Meetings loaded and changed");
        invalidate();
      }
    }

    private void cancelLoadMeetingTask() {
      if (mLoadMeetingsTask != null) {
        mLoadMeetingsTask.cancel(true);
      }
    }

    /**
     * Asynchronous task to load the meetings from the content provider and report the number of
     * meetings back via {@link #onMeetingsLoaded}.
     */
    private class LoadMeetingsTask extends AsyncTask<Void, Void, Boolean> {
      private PowerManager.WakeLock mWakeLock;

      @Override
      protected Boolean doInBackground(Void... voids) {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(
           PowerManager.PARTIAL_WAKE_LOCK, "CalendarWatchFaceWakeLock");
        mWakeLock.acquire();

        long begin = System.currentTimeMillis();
        Uri.Builder builder =
           WearableCalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, begin);
        ContentUris.appendId(builder, begin + DateUtils.DAY_IN_MILLIS);
        final Cursor cursor = getContentResolver().query(builder.build(),
           null, null, null, null);

        Calendar cal = Calendar.getInstance();
        events.clear();

        while (cursor.moveToNext()) {
          // long id = cursor.getLong(cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID));

          long beginVal = cursor.getLong(cursor.getColumnIndex(CalendarContract.Instances.BEGIN));
          long endVal = cursor.getLong(cursor.getColumnIndex(CalendarContract.Instances.END));
          String title = cursor.getString(cursor.getColumnIndex(CalendarContract.Instances.TITLE));
          String eventColor = cursor.getString(cursor.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR));
          Boolean isAllDay = !cursor.getString(cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)).equals("0");
          Boolean isAttending = !cursor.getString(cursor.getColumnIndex(CalendarContract.Instances.SELF_ATTENDEE_STATUS)).equals("2");
          String location = cursor.getString(cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION));

          // Ignore all-day events
          if (isAllDay) continue;

          // Ignore events said no to.
          if (!isAttending) continue;

          // Ignore personal reminders.
          // TODO: find a better way
          if (location.compareTo("personal reminder") == 0) continue;

          CalendarEvent newEvent = new CalendarEvent();
          newEvent.setTitle(title);
          cal.setTimeInMillis(beginVal);
          newEvent.setStart(cal.getTime());
          cal.setTimeInMillis(endVal);
          newEvent.setEnd(cal.getTime());
          newEvent.setDisplayColor(eventColor);
          newEvent.setAllDay(isAllDay);
          newEvent.setAttending(isAttending);
          newEvent.setLocation(location);

          events.add(newEvent);
        }
        cursor.close();
        Collections.sort(events, new CalendarEvent.EventComparator());

        return true;
      }

      @Override
      protected void onPostExecute(Boolean changed) {
        releaseWakeLock();
        onMeetingsLoaded(changed);
      }

      @Override
      protected void onCancelled() {
        releaseWakeLock();
      }

      private void releaseWakeLock() {
        if (mWakeLock != null) {
          mWakeLock.release();
          mWakeLock = null;
        }
      }
    }
  }
}