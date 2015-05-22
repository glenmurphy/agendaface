package com.glenmurphy.agendaface;

import java.util.Comparator;
import java.util.Date;

public class CalendarEvent {
  private String title = "";
  private String location = "";
  private String displayColor;
  private Date startDate;
  private Date endDate;
  private boolean isAllDay = false;
  private boolean isAttending = true;

  public static class EventComparator implements Comparator<CalendarEvent> {
    @Override
    public int compare(CalendarEvent event1, CalendarEvent event2) {
      return event1.getStart().compareTo(event2.getStart());
    }
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Boolean isAttending() {
    return isAttending;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getLocation() {
    return location;
  }

  public void setAttending(boolean attending) {
    isAttending = attending;
  }

  public Date getStart() {
    return startDate;
  }

  public void setStart(Date start) {
    startDate = start;
  }

  public Date getEnd() {
    return endDate;
  }

  public void setEnd(Date end) {
    endDate = end;
  }

  public boolean isAllDay() {
    return isAllDay;
  }

  protected void setAllDay(boolean isAllDay) {
    this.isAllDay = isAllDay;
  }

  public String getDisplayColor() {
    return displayColor;
  }

  public void setDisplayColor(String displayColor) {
    this.displayColor = displayColor;
  }
}
