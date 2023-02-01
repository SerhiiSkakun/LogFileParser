package com.trackensure;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class LogFileParserDelegate {
    private static final Class<LogFileParserDelegate> CLAZZ = LogFileParserDelegate.class;
    private static final Logger logger = Logger.getLogger(CLAZZ);

    public void parseLogFile(HttpServletRequest request, HttpServletResponse response) throws TEAppException {
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

            List<File> sourceFiles;
            String outputFileName;
            if(Objects.isNull(fileName) || fileName.isEmpty()) {
                File sourceDir = new File(filePath);
                if(!sourceDir.isDirectory())
                    throw new TEAppException("Source directory is not present.");
                File[] filesArray = sourceDir.listFiles();
                if(Objects.isNull(filesArray) || filesArray.length == 0)
                    throw new TEAppException("Source directory is empty.");
                sourceFiles = Arrays.stream(filesArray)
                        .sorted(Comparator.comparing(File::getName))
                        .collect(Collectors.toList());
                String[] pathNameTokens = filePath.split("/");
                outputFileName = pathNameTokens[pathNameTokens.length - 1] + ".xlsx";
            } else {
                sourceFiles = new ArrayList<>();
                sourceFiles.add(new File(filePath + "/" + fileName));
                outputFileName = fileName + ".xlsx";
            }

            LogFileParser logFileParser = new LogFileParser(sourceFiles, isUniqRecords, isGatherMessages, isErrorsOnly, isTeStackTraceOnly, startRow, finishRow);

            List<List<LogRecord>> logRecordListAssembled = logFileParser.parseLogFiles();

            LogFileParserXLS logFileParserXLS = new LogFileParserXLS();
            logFileParserXLS.generateAndSendExcelFile(response, logRecordListAssembled, outputFileName);
        } catch (TEAppException | JSONException e) {
            logger.error("parseLogFile()", e);
            throw new TEAppException(e.getMessage(), e.getCause());
        }
    }
}
