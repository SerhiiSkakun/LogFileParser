package com.trackensure;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LogFileParser {
    private static final Class<LogFileParser> CLAZZ = LogFileParser.class;
    private static final Logger logger = Logger.getLogger(CLAZZ);

    private final int MAX_ROWS_FOR_SHEET = 1_000_000;
    private final double CONFORMITY_POWER = 0.80;
    private final Pattern LOGFILE_DATE_TIME_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3} ");
    private List<Pattern> MESSAGE_PATTERN_LIST;

    private final List<File> sourceFiles;
    private final boolean isUniqRecords;
    private final boolean isGatherMessages;
    private final boolean isErrorsOnly;
    private final boolean isTeStackTraceOnly;
    private final int startRow;
    private final int finishRow;

    private String logName;
    private LogRecord record;
    private List<String> stackTrace;
    private List<String> error;

    private boolean wasMessage = false;
    private boolean wasStackTrace = false;
    private boolean isMoreThanLimitRow = false;
    private boolean isNeedToInterrupt = false;

    public LogFileParser(List<File> sourceFiles, boolean isUniqRecords, boolean isGatherMessages,
                         boolean isErrorsOnly, boolean isTeStackTraceOnly, int startRow, int finishRow) throws TEAppException {
        this.sourceFiles = sourceFiles;
        this.isUniqRecords = isUniqRecords;
        this.isGatherMessages = isGatherMessages;
        this.isErrorsOnly = isErrorsOnly;
        this.isTeStackTraceOnly = isTeStackTraceOnly;
        this.startRow = startRow;
        this.finishRow = finishRow;
        if(isGatherMessages){
            MESSAGE_PATTERN_LIST = new ArrayList<Pattern>(){{
                add(Pattern.compile("(?s)^(.*?)\\b*(\\d{2,4}-\\d{2}-\\d{2,4} \\d{2}:\\d{2}:\\d{2}[.,]?\\d*)\\b*(.*?)$"));   // 2023-02-02 22:25.123
                add(Pattern.compile("(?s)^(.*?)\\b*(\\d{2}-\\w{3}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2})\\b*(.*?)$"));              // 04-Dec-22 05:24:22
                add(Pattern.compile("(?s)^(.*?)\\b*(\\d+\\.\\d+\\.\\d+\\.\\d+\\.\\d+)\\b*(.*?)$"));                         //14.123.02.86.7
                add(Pattern.compile("(?s)^(.*?)\\b*(\\d+\\.\\d+\\.\\d+\\.\\d+)\\b*(.*?)$"));                                //141.101.238.127
                add(Pattern.compile("(?s)^(.*?\\b*deviceToke=)([A-z0-9:-]+)\\b*(.*?)$"));                                   //deviceToke=cLZ5miNHQfmsMtlPRQvLIP:APA91bF3mKk1OwBjyvJ8P6pC6t-StR3wOuJTmTj--DYVoUZulsDWTWcMQ1WZ_t1tjHB_pjCQuSjoT6iRtLYEbUEZ1LgwIZdtO6yy3JhtIAkc90oVuO-WbJbRVb2Q1_GAFR2UzZX9z88_q13eJhWbLnkISqdZIjBeMnwa7W3cUrYn_i
                add(Pattern.compile("(?s)^(.*?)\\b*(-\\d+\\.?\\d+)\\b*(.*?)$"));                                            // -25.897
                add(Pattern.compile("(?s)^(.*?)\\b*(\\d+\\.\\d+)\\b*(.*?)$"));                                              // 25.897
                add(Pattern.compile("(?s)^(.*?\\b*)(O\\w{1,2}-[Dd]uty)\\b*(.*?)$"));                                        //On-Duty
            }};
        }
    }

    public List<List<LogRecord>> parseLogFiles() throws TEAppException {
        Collection<LogRecord> logRecordCollection = readFilesToCollection();
        //second pass if needed (joining similar records)
        List<LogRecord> ListOfUniqLogRecords = (isGatherMessages) ? joinRecordWithSimilarMessages(logRecordCollection) : new ArrayList<>(logRecordCollection);
        //sort and split collection by MAX_ROWS_FOR_SHEET records
        Collections.sort(ListOfUniqLogRecords);
        return splitCollectionToSheets(ListOfUniqLogRecords);
    }

    private Collection<LogRecord> readFilesToCollection() throws TEAppException {
        Collection<LogRecord> logRecordCollection = (isUniqRecords) ? new LinkedHashSet<>() : new ArrayList<>();
        Map<Integer, Integer> similarRowsQuantityMapByHash = (isUniqRecords) ? new HashMap<>() : null;
        for (File sourceFile : sourceFiles) {
            try {
                LineNumberReader reader = new LineNumberReader(new FileReader(sourceFile));
                logger.info("readAllFilesToCollection(): start reading file " + sourceFile.getName() + ".");
                this.logName = sourceFile.getName();
                reader.lines()
                        .skip((startRow > 0) ? startRow - 1 : 0)
                        .filter(row -> !isNeedToInterrupt)
                        .forEach(row -> readRowFromFile(logRecordCollection, row.trim(), reader.getLineNumber(), similarRowsQuantityMapByHash));
                reader.close();
                logger.info("readFilesToCollection(): finish reading file " + sourceFile.getName() + ".");
            } catch (FileNotFoundException e) {
                logger.error("readFilesToCollection(): file " + sourceFile.getName() + " not exist." , e);
                throw new TEAppException("File not exist: " + sourceFile.getName(), e);
            } catch (IOException e) {
                logger.error("readFilesToCollection: error while reading file " + sourceFile.getName(), e);
                throw new TEAppException("Error while reading file: " + sourceFile.getName(), e);
            }
        }
        recordAdd(record, stackTrace, error, logRecordCollection, similarRowsQuantityMapByHash); //add last record of last file

        for (LogRecord logRecord : logRecordCollection) {
            logRecord.setSimilarRowsQuantity((isUniqRecords) ? similarRowsQuantityMapByHash.get(logRecord.hashCode()) : 1);
        }

        return logRecordCollection;
    }

    private void readRowFromFile(Collection<LogRecord> logRecordCollection, String row, int rowNumber, Map<Integer, Integer> similarRowsQuantityMapByHash) throws RuntimeException {
        try {
            if (finishRow != 0 && rowNumber > finishRow)
                isMoreThanLimitRow = true;
            if (row.contains("** /")) { // label of start log
                this.logName = row.substring(row.lastIndexOf("/") + 1, row.indexOf(" **"));
                if (Objects.nonNull(error)) {
                    if (Objects.isNull(record)) {
                        record = new LogRecord();
                        record.setRowNumber(rowNumber);
                    }
                    record.setError(error);
                }
                wasMessage = false;
                wasStackTrace = false;
            } else if (LOGFILE_DATE_TIME_PATTERN.matcher(row).find()) { //row starts with date-time
                if (!isMoreThanLimitRow) {
                    recordAdd(record, stackTrace, error, logRecordCollection, similarRowsQuantityMapByHash);
                    error = null;
                    stackTrace = null;
                    record = fillMainFields(row);
                    record.setRowNumber(rowNumber);
                    wasMessage = true;
                    wasStackTrace = false;
                } else
                    isNeedToInterrupt = true;
            } else if (row.startsWith("at ") || row.matches("... \\d+ more")) { //if stackTrace row
                stackTrace = fillStackTrace(row, stackTrace, isTeStackTraceOnly);
                wasMessage = false;
                wasStackTrace = true;
            } else if (row.contains("## /")) { //label of end log)
                    if (!isMoreThanLimitRow) {
                    recordAdd(record, stackTrace, error, logRecordCollection, similarRowsQuantityMapByHash);
                    error = null;
                    stackTrace = null;
                    logName = "";
                    record = null;
                    wasMessage = false;
                    wasStackTrace = false;
                } else
                    isNeedToInterrupt = true;
            } else if (!row.equals("") && !row.equals("--")) { //other (unparsed) row
                if (wasMessage) {
                    if (Objects.isNull(record.getMessage())) {
                        record.setMessage(new ArrayList<>());
                    }
                    record.getMessage().add(row);
                } else if (wasStackTrace) {
                    stackTrace.add(row);
                } else {
                    if (!isMoreThanLimitRow) {
                        if (Objects.isNull(record)) {
                            record = new LogRecord();
                            record.setRowNumber(rowNumber);
                        }
                        if (Objects.isNull(error)) {
                            error = new ArrayList<>();
                        }
                        error.add(row);
                    } else
                        isNeedToInterrupt = true;
                }
            }
        } catch (Exception e) {
            logger.error("parseFileToRecords()", e);
            throw new RuntimeException(e.getMessage(), e.getCause());
        }
    }

    private LogRecord fillMainFields(String row) {
        String dateString = row.substring(0, 10);
        String timeString = row.substring(11, 23).replace(',', '.');
        String priorityString = row.substring(24, 29).trim();
        String thread = row.substring(row.indexOf("[") + 1, row.indexOf("] ")).trim();
        String category = row.substring(row.indexOf("] ") + 1, row.indexOf(" -", row.indexOf("] ") + 1)).trim();
        String messageString = row.substring((row.indexOf(" -", row.indexOf("] ") + 1) + 2))
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
        if (Objects.isNull(stackTrace)) {
            stackTrace = new ArrayList<>();
        }
        String stackTraceRow = (row.startsWith("at ")) ? row.substring(3) : row;
        if (!isTeStackTraceOnly || !(stackTraceRow.startsWith("java")
                || stackTraceRow.startsWith("org.")
                || stackTraceRow.startsWith("com.zaxxer.hikari.pool")
                || stackTraceRow.startsWith("com.sun.")
                || stackTraceRow.startsWith("it.sauronsoftware.")
                || stackTraceRow.startsWith("sun."))) {
            stackTrace.add(stackTraceRow);
        }
        return stackTrace;
    }

    private void recordAdd(LogRecord record, List<String> stackTrace, List<String> error, Collection<LogRecord> logRecordCollection, Map<Integer, Integer> similarRowsQuantityMapByHash) {
        if (Objects.isNull(record) || (isErrorsOnly && record.getPriority() != Level.ERROR && record.getPriority() != Level.FATAL && record.getPriority() != Level.OFF)) return;
        if (Objects.nonNull(stackTrace)) {
            record.setStackTrace(stackTrace);
            record.setStackTraceStr(String.join(System.lineSeparator(), record.getStackTrace()));
        }
        if (Objects.nonNull(record.getMessage()) && !record.getMessage().isEmpty()) {
            String messageStr = String.join(System.lineSeparator(), record.getMessage());
            record.setMessageStr(messageStr);
            List<String> tokenList = new ArrayList<>();
            createTokenList(messageStr, tokenList);
            record.setMessageTokens(tokenList);
        }
        if (Objects.nonNull(error)) {
            record.setError(error);
            record.setErrorStr(String.join(System.lineSeparator(), record.getError()));
        }
        logRecordCollection.add(record);
        if (isUniqRecords) {
            similarRowsQuantityMapByHash.merge(record.hashCode(), 1, Integer::sum);
        }
    }

    private void createTokenList(String messageStr, List<String> tokenList) {
        if(messageStr.isEmpty()) return;
        if(isGatherMessages) {
            for (Pattern pattern : MESSAGE_PATTERN_LIST) {
                Matcher matcher = pattern.matcher(messageStr);
                if (matcher.find()) {
                    createTokenList(messageStr.substring(matcher.start(1), matcher.end(1)), tokenList);
                    tokenList.add(messageStr.substring(matcher.start(2), matcher.end(2)));
                    createTokenList(messageStr.substring(matcher.start(3), matcher.end(3)), tokenList);
                    return;
                }
            }
        }
        tokenList.addAll(Arrays.asList(messageStr.split("\\b")));
    }

    private List<LogRecord> joinRecordWithSimilarMessages(Collection<LogRecord> logRecordCollection) throws TEAppException{
        logger.info("joinRecordWithSimilarMessages(): start removing record with similar messages.");
        Map<String, Set<LogRecord>> duplicatesMap = logRecordCollection.stream()
                .collect(Collectors.groupingBy(logRecord -> logRecord.getPriority() + logRecord.getCategory() + logRecord.getStackTraceStr(), Collectors.toCollection(TreeSet<LogRecord>::new)));
        List<LogRecord> allUniqLogRecordsList = new ArrayList<>();
        List<LogRecord> oneGroupOfUniqLogRecordsList;
        boolean isUniq;
        try {
            for (Set<LogRecord> duplicatesSet : duplicatesMap.values()) {
                oneGroupOfUniqLogRecordsList = new ArrayList<>();
                LogRecord firstLogRecord = duplicatesSet.stream().findFirst().orElse(null);
                oneGroupOfUniqLogRecordsList.add(firstLogRecord);
                duplicatesSet.remove(firstLogRecord);
                for (LogRecord notFirstLogRecord : duplicatesSet) {
                    isUniq = true;
                    for (LogRecord uniqLogRecord : oneGroupOfUniqLogRecordsList) {
                        if (isEqualLogRecords(uniqLogRecord, notFirstLogRecord)) {
                            join2LogRecords(uniqLogRecord, notFirstLogRecord);
                            isUniq = false;
                            break;
                        }
                    }
                    if (isUniq) oneGroupOfUniqLogRecordsList.add(notFirstLogRecord);
                }
                for (LogRecord uniqLogRecord : oneGroupOfUniqLogRecordsList) {
                    Map<Integer, Set<String>> logRecordValuesMap = uniqLogRecord.getMessageValues();
                    if (Objects.nonNull(logRecordValuesMap)) {
                        uniqLogRecord.setMessageValuesStr(logRecordValuesMap
                                .entrySet().stream()
                                .map(entry -> "${" + entry.getKey() + "} = " + String.join(" / ", entry.getValue()))
                                .collect(Collectors.joining(System.lineSeparator()))
                        );
                        uniqLogRecord.setMessageStr(String.join("", uniqLogRecord.getMessageTokens()));
                    }
                }
                allUniqLogRecordsList.addAll(oneGroupOfUniqLogRecordsList);
            }
        } catch (Exception e) {
            logger.error("joinRecordWithSimilarMessages()", e);
            throw new TEAppException(e.getMessage(), e.getCause());
        }
        logger.info("joinRecordWithSimilarMessages(): finish removing record with similar messages.");
        return allUniqLogRecordsList;
    }

    private boolean isEqualLogRecords (LogRecord logRecord1, LogRecord logRecord2) {
        int equalTokenCount = 0;
        if(Objects.isNull(logRecord1.getMessageTokens()) || Objects.isNull(logRecord2.getMessageTokens())) return false;
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
                        if (Objects.isNull(mainLogRecordValuesMap)) {
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
            logger.error("join2LogRecords(): mainLogRecord = " + mainLogRecord + "addedLogRecord = " + addedLogRecord, e);
            throw new TEAppException(e.getMessage(), e.getCause());
        }
    }

    private List<List<LogRecord>> splitCollectionToSheets(List<LogRecord> uniqLogRecordListAssembled) throws TEAppException {
        logger.info("splitCollectionToSheets(): start splitting collection to sheets.");
        List<List<LogRecord>> logRecordListAssembled = new ArrayList<>();
        int skipRowsNumber = 0;
        int sheetQuantity = uniqLogRecordListAssembled.size() / MAX_ROWS_FOR_SHEET + 1;
        for (int i = 0; i < sheetQuantity; i++) {
            List<LogRecord> maxRowsForSheetLogRecordList = new ArrayList<>();
            try {
                uniqLogRecordListAssembled.stream()
                        .skip(skipRowsNumber)
                        .limit(MAX_ROWS_FOR_SHEET)
                        .forEach(maxRowsForSheetLogRecordList::add);
            } catch (Exception e) {
                logger.error("splitCollectionToSheets(): collection size = " + uniqLogRecordListAssembled.size()
                        + ", MAX_ROWS_FOR_SHEET = " + MAX_ROWS_FOR_SHEET + ", sheet = " + i + " of " + sheetQuantity, e);
                throw new TEAppException(e.getMessage(), e.getCause());
            }
            logRecordListAssembled.add(maxRowsForSheetLogRecordList);
            skipRowsNumber += MAX_ROWS_FOR_SHEET;
        }
        logger.info("splitCollectionToSheets(): finish splitting collection to sheets.");
        return logRecordListAssembled;
    }
}
