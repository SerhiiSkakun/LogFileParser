package com.trackensure;

import org.apache.log4j.Level;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class LogRecord implements Comparable<LogRecord> {
    private int rowNumber;
    private String logName;
    private LocalDate date;
    private LocalTime time;
    private Level priority;
    private String thread;
    private String category;
    private List<String> message;
    private String messageStr;
    private List<String> stackTrace;
    private String stackTraceStr;
    private List<String> error;
    private String errorStr;
    private List<String> messageTokens;
    private Map<Integer, Set<String>> messageValues;
    private String messageValuesStr;
    private int similarRowsQuantity;

    public LogRecord() {
    }

    public LogRecord(String logName, LocalDate date, LocalTime time, Level priority, String thread, String category, List<String> message) {
        this.logName = logName;
        this.date = date;
        this.time = time;
        this.priority = priority;
        this.thread = thread;
        this.category = category;
        this.message = message;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getLogName() {
        return logName;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    public Level getPriority() {
        return priority;
    }

    public String getThread() {
        return thread;
    }

    public void setThread(String thread) {
        this.thread = thread;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getMessage() {
        return message;
    }

    public void setMessage(List<String> message) {
        this.message = message;
    }

    public String getMessageStr() {
        return messageStr;
    }

    public void setMessageStr(String messageStr) {
        this.messageStr = messageStr;
    }

    public List<String> getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(List<String> stackTrace) {
        this.stackTrace = stackTrace;
    }

    public String getStackTraceStr() {
        return stackTraceStr;
    }

    public void setStackTraceStr(String stackTraceStr) {
        this.stackTraceStr = stackTraceStr;
    }

    public List<String> getError() {
        return error;
    }

    public void setError(List<String> error) {
        this.error = error;
    }

    public String getErrorStr() {
        return errorStr;
    }

    public void setErrorStr(String errorStr) {
        this.errorStr = errorStr;
    }

    public List<String> getMessageTokens() {
        return messageTokens;
    }

    public void setMessageTokens(List<String> messageTokens) {
        this.messageTokens = messageTokens;
    }

    public Map<Integer, Set<String>> getMessageValues() {
        return messageValues;
    }

    public String getMessageValuesStr() {
        return messageValuesStr;
    }

    public void setMessageValuesStr(String messageValuesStr) {
        this.messageValuesStr = messageValuesStr;
    }

    public void setMessageValues(Map<Integer, Set<String>> messageValues) {
        this.messageValues = messageValues;
    }

    public int getSimilarRowsQuantity() {
        return similarRowsQuantity;
    }

    public void setSimilarRowsQuantity(int similarRowsQuantity) {
        this.similarRowsQuantity = similarRowsQuantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogRecord record = (LogRecord) o;
        return Objects.equals(category, record.category) && Objects.equals(priority, record.priority) && Objects.equals(messageStr, record.messageStr) && Objects.equals(stackTraceStr, record.stackTraceStr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, priority, messageStr, stackTraceStr);
    }

    @Override
    public int compareTo(LogRecord o) {
        return this.rowNumber - o.rowNumber;
    }
}

