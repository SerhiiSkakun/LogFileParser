package com.trackensure;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LogFileParser {
    private static final Class<LogFileParser> CLAZZ = LogFileParser.class;
    private static final Logger logger = Logger.getLogger(CLAZZ);

    final int MAX_ROWS_FOR_SHEET = 1_000_000;
    final double CONFORMITY_POWER = 0.75;
    private final String parsedFileName;
    private final Reader reader;
    private final boolean isUniqRecords;
    private final boolean isGatherMessages;
    private final boolean isErrorsOnly;
    private final boolean isTeStackTraceOnly;
    private final int startRow;
    private final int finishRow;

    public LogFileParser(String parsedFilePath, String parsedFileName, boolean isUniqRecords, boolean isGatherMessages, boolean isErrorsOnly, boolean isTeStackTraceOnly, int startRow, int finishRow) throws TEAppException {
        this.parsedFileName = parsedFileName;
        String fullFileName = parsedFilePath + "/" + parsedFileName;
        this.isUniqRecords = isUniqRecords;
        this.isGatherMessages = isGatherMessages;
        this.isErrorsOnly = isErrorsOnly;
        this.isTeStackTraceOnly = isTeStackTraceOnly;
        this.startRow = startRow;
        this.finishRow = finishRow;
        try {
            this.reader = new FileReader(fullFileName);
            logger.info("LogFileParser() - file exist - " + fullFileName + ".");
        } catch (FileNotFoundException e) {
            logger.error("File not exist: " + fullFileName, e);
            throw new TEAppException(e.getMessage(), e.getCause());
        }
    }

    public List<List<LogRecord>> parseLogFile() throws TEAppException{
        Collection<LogRecord> logRecordCollection = (isUniqRecords) ? new LinkedHashSet<>() : new ArrayList<>();
        Map<Integer, Integer> similarRowsQuantityMapByHash = (isUniqRecords) ? new HashMap<>() : null;
        LogRecord record = null;
        String row;
        List<String> stackTrace = null;
        List<String> error = null;
        boolean wasMessage = false;
        boolean wasStackTrace = false;
        boolean needInterrupt = false;
        String logName = parsedFileName;

        Pattern pattern = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3} ");
        logger.info("parseLogFile() - start parse file to records.");
        try (LineNumberReader bufferedReader = new LineNumberReader(reader)) {
            if (startRow > 0 && finishRow > startRow) {
                while (bufferedReader.getLineNumber() < startRow -1) {
                    bufferedReader.readLine();
                }
            }
            while ((row = bufferedReader.readLine()) != null) {
                if (finishRow != 0 && bufferedReader.getLineNumber() > finishRow) {
                    needInterrupt = true;
                }
                row = row.trim();
                if (row.contains("** /")) { // label of start log
                    logName = row.substring(row.lastIndexOf("/")+1, row.indexOf(" **"));
                    if (error != null) {
                        if (record == null) {
                            record = new LogRecord();
                            record.setRowNumber(bufferedReader.getLineNumber());
                        }
                        record.setError(error);
                    }
                    wasMessage = false;
                    wasStackTrace = false;
                } else if (pattern.matcher(row).find()) { //row starts with date-time
                    if(needInterrupt) break;
                    if (record != null && (!isErrorsOnly || record.getPriority() == Level.ERROR)) {
                        recordAdd(record, stackTrace, error, logRecordCollection, similarRowsQuantityMapByHash);
                    }
                    error = null;
                    stackTrace = null;
                    record = fillMainFields(row, logName);
                    record.setRowNumber(bufferedReader.getLineNumber());
                    wasMessage = true;
                    wasStackTrace = false;
                } else if (row.startsWith("at ") || row.matches("... \\d+ more")) { //if stackTrace row
                    stackTrace = fillStackTrace (row, stackTrace, isTeStackTraceOnly);
                    wasMessage = false;
                    wasStackTrace = true;
                } else if (row.contains("## /")) { //label of end log)
                    if(needInterrupt) break;
                    if (record != null && (!isErrorsOnly || record.getPriority() == Level.ERROR)) {
                        recordAdd(record, stackTrace, error, logRecordCollection, similarRowsQuantityMapByHash);
                    }
                    error = null;
                    stackTrace = null;
                    logName = "";
                    record = null;
                    wasMessage = false;
                    wasStackTrace = false;
                } else if (row.equals("") || row.equals("--")) { //empty row)
                    continue;
                } else { //other (unparsed) row
                    if (wasMessage) {
                        if (record.getMessage() == null) {
                            record.setMessage(new ArrayList<>());
                        }
                        record.getMessage().add(row);
                    } else if (wasStackTrace) {
                        stackTrace.add(row);
                    } else {
                        if(needInterrupt) break;
                        if (record == null) {
                            record = new LogRecord();
                            record.setRowNumber(bufferedReader.getLineNumber());
                        }
                        if (error == null) {
                            error = new ArrayList<>();
                        }
                        error.add(row);
                    }
                }
            }
            if (record != null && (!isErrorsOnly || record.getPriority() == Level.ERROR)) {
                recordAdd(record, stackTrace, error, logRecordCollection, similarRowsQuantityMapByHash);
            }
            logRecordCollection.forEach(logRecord -> logRecord.setSimilarRowsQuantity((isUniqRecords) ? similarRowsQuantityMapByHash.get(logRecord.hashCode()) : 1));
            logger.info("parseLogFile() - finish parse file to records.");
            List<LogRecord> uniqLogRecordListAssembled = (isGatherMessages) ? joinRecordWithMessageDuplicates (logRecordCollection) : new ArrayList<>(logRecordCollection);
            uniqLogRecordListAssembled = uniqLogRecordListAssembled.stream().sorted(Comparator.comparing(LogRecord::getRowNumber)).collect(Collectors.toList());
            return splitListToSheets(uniqLogRecordListAssembled);
        } catch (IOException e) {
            logger.error("parseLogFile()", e);
            throw new TEAppException(e.getMessage(), e.getCause());
        }
    }

    private LogRecord fillMainFields(String row, String logName) {
        String dateString = row.substring(0, 10);
        String timeString = row.substring(11, 23).replace(',', '.');
        String priorityString = row.substring(24, 29).trim();
        String thread = row.substring(row.indexOf("[") + 1, row.indexOf("] ")).trim();
        String category = row.substring(row.indexOf("] ") + 1, row.indexOf(" -")).trim();
        String messageString = row.substring((row.indexOf(" -") + 2))
                .replace("\t\t", "\t")
                .replace("\\s\t", "\\s")
                .replace("\t\\s", "\\s")
                .trim();
        List<String> message = null;
        if (!messageString.equals("")) {
            message = new ArrayList<>();
            message.add(messageString);
        }
        LocalDate date = LocalDate.parse(dateString);
        LocalTime time = LocalTime.parse(timeString);
        Level priority = Level.toLevel(priorityString);
        return new LogRecord(logName, date, time, priority, thread, category, message);
    }

    private List<String> fillStackTrace(String row, List<String> stackTrace, boolean isTeStackTraceOnly) {
        if (stackTrace == null) {
            stackTrace = new ArrayList<>();
        }
        String stackTraceRow = (row.startsWith("at ")) ? row.substring(3) : row;
        if (!isTeStackTraceOnly || !(stackTraceRow.startsWith("java")
                || stackTraceRow.startsWith("org.")
                || stackTraceRow.startsWith("com.zaxxer.hikari.pool")
                || stackTraceRow.startsWith("com.sun.")
                || stackTraceRow.startsWith("sun.security."))) {
            stackTrace.add(stackTraceRow);
        }
        return stackTrace;
    }

    private void recordAdd(LogRecord record, List<String> stackTrace, List<String> error, Collection<LogRecord> logRecordCollection, Map<Integer, Integer> similarRowsQuantityMapByHash) {
        if (stackTrace != null) {
            record.setStackTrace(stackTrace);
            record.setStackTraceStr(String.join(System.lineSeparator(), record.getStackTrace()));
        }
        if (record.getMessage() != null && record.getMessage().size() > 0) {
            String messageStr = String.join(System.lineSeparator(), record.getMessage());
            record.setMessageStr(messageStr);
            List<String> tokenList = Arrays.asList(messageStr.split("\\b"));
            record.setMessageTokens(tokenList);
        }
        if (error != null) {
            record.setError(error);
            record.setErrorStr(String.join(System.lineSeparator(), record.getError()));
        }
        int hash = record.hashCode();
        if (isUniqRecords) {
            if (logRecordCollection.add(record)) {
                similarRowsQuantityMapByHash.put(hash, 1);
            } else {
                Integer value = similarRowsQuantityMapByHash.get(hash);
                similarRowsQuantityMapByHash.put(hash, ++value);
            }
        } else {
            logRecordCollection.add(record);
        }
    }

    private List<LogRecord> joinRecordWithMessageDuplicates(Collection<LogRecord> logRecordCollection) throws TEAppException{
        logger.info("joinRecordWithMessageDuplicates() - start remove message duplicates.");
        Map<String, Set<LogRecord>> duplicatesMap = logRecordCollection.stream()
                .collect(Collectors.groupingBy(logRecord -> logRecord.getPriority() + logRecord.getCategory() + logRecord.getStackTraceStr(), Collectors.toSet()));
        List<LogRecord> uniqLogRecordListAssembled = new ArrayList<>();
        List<LogRecord> uniqLogRecordList;
        boolean isUniq;
        try {
            for (Set<LogRecord> duplicatesSet : duplicatesMap.values()) {
                uniqLogRecordList = new ArrayList<>();
                LogRecord firstLogRecord = duplicatesSet.stream().findFirst().orElse(null);
                uniqLogRecordList.add(firstLogRecord);
                duplicatesSet.remove(firstLogRecord);
                for (LogRecord notFirstLogRecord : duplicatesSet) {
                    isUniq = true;
                    for (LogRecord uniqLogRecord : uniqLogRecordList) {
                        if (isEqualLogRecords(uniqLogRecord, notFirstLogRecord)) {
                            join2LogRecords(uniqLogRecord, notFirstLogRecord);
                            isUniq = false;
                            break;
                        }
                     }
                    if (isUniq) uniqLogRecordList.add(notFirstLogRecord);
                }
                for (LogRecord uniqLogRecord : uniqLogRecordList) {
                    Map<Integer, Set<String>> logRecordValuesMap = uniqLogRecord.getMessageValues();
                    if (logRecordValuesMap != null) {
                        uniqLogRecord.setMessageValuesStr(logRecordValuesMap
                                .entrySet().stream()
                                .map(entry -> "${" + entry.getKey() + "} = " + String.join(" / ", entry.getValue()))
                                .collect(Collectors.joining(System.lineSeparator()))
                        );
                        uniqLogRecord.setMessageStr(String.join("", uniqLogRecord.getMessageTokens()));
                    }
                }
                uniqLogRecordListAssembled.addAll(uniqLogRecordList);
            }
        } catch (TEAppException e) {
            logger.error("joinRecordWithMessageDuplicates()", e);
            throw new TEAppException(e.getMessage(), e.getCause());
        }
        logger.info("joinRecordWithMessageDuplicates() - finish remove message duplicates.");
        return uniqLogRecordListAssembled;
    }

    private boolean isEqualLogRecords (LogRecord logRecord1, LogRecord logRecord2) {
        int equalTokenCount = 0;
        if(logRecord1.getMessageTokens() == null || logRecord2.getMessageTokens() == null) return false;
        else if (logRecord1.getMessageTokens().size() != logRecord2.getMessageTokens().size()) return false;
        else if (logRecord1.getMessageTokens().size() <= 1) return false;
        else {
            for (int i = 0; i < logRecord1.getMessageTokens().size() ; i++) {
                if (logRecord1.getMessageTokens().get(i).equals(logRecord2.getMessageTokens().get(i))) equalTokenCount++;
            }
            return ( (double) equalTokenCount / logRecord1.getMessageTokens().size() > CONFORMITY_POWER);
        }
    }

    private void join2LogRecords (LogRecord mainLogRecord, LogRecord addedLogRecord) throws TEAppException {
        try {
            Map<Integer, Set<String>> mainLogRecordValuesMap = mainLogRecord.getMessageValues();
            List<String> mainLogRecordMessageTokenList = mainLogRecord.getMessageTokens();
            List<String> addedLogRecordMessageTokenList = addedLogRecord.getMessageTokens();
            for (int i = 0; i < addedLogRecordMessageTokenList.size(); i++) {
                String mainLogRecordMessageToken = mainLogRecordMessageTokenList.get(i);
                String addedLogRecordMessageToken = addedLogRecordMessageTokenList.get(i);
                if (!mainLogRecordMessageToken.equals(addedLogRecordMessageToken)) {
                    if (mainLogRecordMessageToken.startsWith("${")) {
                        mainLogRecordValuesMap.get(i).add(addedLogRecordMessageToken);
                    } else {
                        if (mainLogRecordValuesMap == null) {
                            mainLogRecordValuesMap = new TreeMap<>();
                            mainLogRecord.setMessageValues(mainLogRecordValuesMap);
                        }
                        Set<String> mainLogRecordValuesSet = new TreeSet<>();
                        mainLogRecordValuesSet.add(mainLogRecordMessageToken);
                        mainLogRecordValuesSet.add(addedLogRecordMessageToken);
                        mainLogRecordValuesMap.put(i,mainLogRecordValuesSet);
                        mainLogRecordMessageTokenList.set(i, "${" + i + "}");
                    }
                }
            }
            mainLogRecord.setSimilarRowsQuantity(mainLogRecord.getSimilarRowsQuantity() + addedLogRecord.getSimilarRowsQuantity());
        } catch (Exception e) {
            logger.error("join2LogRecords()", e);
            throw new TEAppException(e.getMessage(), e.getCause());
        }
    }

    private List<List<LogRecord>> splitListToSheets(List<LogRecord> uniqLogRecordListAssembled) {
        List<List<LogRecord>> logRecordListAssembled = new ArrayList<>();
        List<LogRecord> maxRowsForSheetLogRecordList = new ArrayList<>();
        for (int i = 0; i < uniqLogRecordListAssembled.size() / MAX_ROWS_FOR_SHEET + 1; i++) {
            uniqLogRecordListAssembled.stream().limit(MAX_ROWS_FOR_SHEET).forEach(maxRowsForSheetLogRecordList::add);
            logRecordListAssembled.add(maxRowsForSheetLogRecordList);
        }
        return logRecordListAssembled;
    }
}