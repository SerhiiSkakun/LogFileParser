package com.trackensure;

import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.util.List;

import static org.apache.poi.ss.usermodel.CellStyle.*;

public class LogFileParserXLS {

    private static final Class<LogFileParserXLS> CLAZZ = LogFileParserXLS.class;
    private static final Logger logger = Logger.getLogger(CLAZZ);
    final int MAX_CELL_LENGTH = 32_767;

    CellStyle arial12BoldFontCellStyle;
    CellStyle arial11CellStyleError;

    public void generateAndSendExcelFile(HttpServletResponse response, List<List<LogRecord>> logRecordListAssembled, String fileName) throws TEAppException {
        SXSSFWorkbook wb = null;
        try {
            wb = new SXSSFWorkbook();
            wb.setCompressTempFiles(true);
            logger.info("generateAndSendExcelFile(): start write book.");
            for (int i = 0; i < logRecordListAssembled.size(); i++) {
                List<LogRecord> dataList = logRecordListAssembled.get(i);
                generateSheet(wb, dataList, ((Integer)i).toString());
            }
            logger.info("generateAndSendExcelFile(): finish write book.");
            prepareResponseToSendExcelFile(response, fileName);
            OutputStream outputStream = response.getOutputStream();
            wb.write(outputStream);
            outputStream.close();
            logger.info("generateAndSendExcelFile(): file has been sent: " + fileName + ".");
        } catch (Exception e) {
            logger.error("Exception in LogFileParserXLS generateExcelFile()", e);
            throw new TEAppException(e.getMessage(), e);
        } finally {
            if (wb != null) {
                wb.dispose ();
            }
        }
    }

    private void prepareResponseToSendExcelFile(HttpServletResponse response, String fileName) throws TEAppException {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
            response.setHeader("Set-Cookie", "fileDownload=true; path=/");
        } catch (Exception e) {
            logger.error("prepareResponseToSendXlsFile()", e);
            throw new TEAppException(e.getMessage(), e);
        }
    }

    private void generateSheet(SXSSFWorkbook wb, List<LogRecord> dataList, String sheetName) throws TEAppException {
        logger.info("generateSheet(): start generate sheet " + sheetName + ".");
        Sheet sh = wb.createSheet(sheetName);
        initStyleOfCells(wb);
        Row row = sh.createRow(0);
        String[] titlesOfColumns = {"Row", "Log Name", "Date", "Time", "Priority", "Thread", "Category", "Message", "MessageValues", "Stack Trace", "Similar Rows Quantity", "Not parsed row"};
        for (int i = 0; i < titlesOfColumns.length; i++) {
            Cell cellHeader = row.createCell(i);
            cellHeader.setCellStyle(arial12BoldFontCellStyle);
            cellHeader.setCellValue(titlesOfColumns[i]);
        }
        LogRecord data = null;
        int column = 0;
        try {
            Cell cell;
            for (int rowCount = 1; (rowCount <= dataList.size()); rowCount++) {
                row = sh.createRow(rowCount);
                data = dataList.get(rowCount - 1);

                column = 0;
                //A Row
                cell = row.createCell(column++);
                cell.setCellValue(data.getRowNumber());
                //B Log Name
                cell = row.createCell(column++);
                cell.setCellValue((data.getLogName() != null) ? data.getLogName() : "");
                //C Date
                cell = row.createCell(column++);
                cell.setCellValue((data.getDate() != null) ? data.getDate().toString() : "");
                //D Time
                cell = row.createCell(column++);
                cell.setCellValue((data.getTime() != null) ? data.getTime().toString().replace('.',',') : "");
                //E Priority
                cell = row.createCell(column++);
                cell.setCellValue((data.getPriority() != null) ? data.getPriority().toString() : "");
                //F Thread
                cell = row.createCell(column++);
                cell.setCellValue((data.getThread() != null) ? data.getThread() : "");
                //G Category
                cell = row.createCell(column++);
                cell.setCellValue((data.getCategory() != null) ? data.getCategory() : "");
                //H Message
                cell = row.createCell(column++);
                String messageString = data.getMessageStr();
                setStringValueWithCheck(cell, messageString);
                //I Message Values
                cell = row.createCell(column++);
                String messageValuesString = data.getMessageValuesStr();
                setStringValueWithCheck(cell, messageValuesString);
                //J Stack Trace
                cell = row.createCell(column++);
                String stackTraceString = data.getStackTraceStr();
                setStringValueWithCheck(cell, stackTraceString);
                //K Similar Rows Quantity
                cell = row.createCell(column++);
                cell.setCellValue(data.getSimilarRowsQuantity());
                //L Error
                cell = row.createCell(column);
                String errorString = data.getErrorStr();
                setStringValueWithCheck(cell, errorString);
            }
            logger.info("generateSheet(): finish generate sheet " + sheetName + ".");
        } catch (Exception e) {
            logger.error("generateSheet(). rowNumber = " + data.getRowNumber() + ". column = " + column, e);
            throw new TEAppException(e.getMessage(), e);
        }

        int convertIndex = 34;
        int[] summaryColumnWidthArray = {50, 150, 75, 75, 50, 100, 130, 200, 200, 200, 150, 100};
        for (int x = 0; x < summaryColumnWidthArray.length; x++) {
            sh.setColumnWidth(x, summaryColumnWidthArray[x] * convertIndex);
        }

        sh.setAutoFilter(CellRangeAddress.valueOf("A1:L1"));
        sh.createFreezePane(0, 1);
    }

    private void initStyleOfCells(SXSSFWorkbook wb) {
        Font arial12BoldFont = wb.createFont();
        arial12BoldFont.setFontName("ARIAL");
        arial12BoldFont.setBold(true);
        arial12BoldFont.setFontHeightInPoints((short) 11);

        Font arial11RedFont = wb.createFont();
        arial11RedFont.setColor(IndexedColors.RED.getIndex());
        arial11RedFont.setFontName("ARIAL");
        arial11RedFont.setBold(false);
        arial11RedFont.setFontHeightInPoints((short) 10);

        //Header
        arial12BoldFontCellStyle = wb.createCellStyle();
        arial12BoldFontCellStyle.setFont(arial12BoldFont);
        arial12BoldFontCellStyle.setAlignment(ALIGN_CENTER);

        //Body error
        arial11CellStyleError = wb.createCellStyle();
        arial11CellStyleError.setFont(arial11RedFont);
        arial11CellStyleError.setAlignment(ALIGN_LEFT);
    }

    private void setStringValueWithCheck(Cell cell, String textString) {
        if (textString!=null && textString.length() > 0) {
            if (textString.length() >= MAX_CELL_LENGTH) {
                textString = textString.substring(0, MAX_CELL_LENGTH);
                cell.setCellStyle(arial11CellStyleError);
            }
            cell.setCellValue(textString);
        }
    }
}

