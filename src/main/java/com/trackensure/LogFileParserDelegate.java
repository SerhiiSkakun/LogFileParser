package com.trackensure;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.util.List;

public class LogFileParserDelegate {
    private static final Class<LogFileParserDelegate> CLAZZ = LogFileParserDelegate.class;
    private static final Logger logger = Logger.getLogger(CLAZZ);

    public void parseLogFile(HttpServletRequest request, HttpServletResponse response) throws TEAppException, FileNotFoundException {
        try {
            JSONObject jsonIn = new JSONObject(request.getParameter("data"));
            boolean isUniqRecords = jsonIn.optBoolean("isUniqRecords");
            boolean isGatherMessages = jsonIn.optBoolean("isGatherMessages");
            boolean isErrorsOnly = jsonIn.optBoolean("isErrorsOnly");
            boolean isTeStackTraceOnly = jsonIn.optBoolean("isTeStackTraceOnly");
            int startRow = jsonIn.optInt("startRow");
            int finishRow = jsonIn.optInt("finishRow");
            String filePath = jsonIn.optString("filePath");
            String fileName = jsonIn.optString("fileName");
            LogFileParser logFileParser = new LogFileParser(filePath, fileName, isUniqRecords, isGatherMessages, isErrorsOnly, isTeStackTraceOnly, startRow, finishRow);
            String outputFileName = fileName + ".xlsx";
            List<List<LogRecord>> logRecordListAssembled = logFileParser.parseLogFile();
            LogFileParserXLS logFileParserXLS = new LogFileParserXLS();
            logFileParserXLS.generateAndSendExcelFile(response, logRecordListAssembled, outputFileName);
        } catch (TEAppException | JSONException e) {
            logger.error("parseLogFile()", e);
            throw new TEAppException(e.getMessage(), e.getCause());
        }
    }
}
