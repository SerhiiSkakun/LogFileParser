package com.trackensure;


import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;


public class LogFileParserServlet extends HttpServlet {
    private static final Class<LogFileParserServlet> CLAZZ = LogFileParserServlet.class;
    private static final Logger logger = Logger.getLogger(CLAZZ);

    HttpServletRequest request;
    HttpServletResponse response;

    public static final String ACTION_PARSE_LOG_FILE = "parseLogFile";


    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;

        LogFileParserDelegate logFileParserDelegate = new LogFileParserDelegate();
        String actionName = request.getParameter("actionName");
        if (actionName == null || actionName.isEmpty()) {
            sendStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Not correct actionName");
        } else if (ACTION_PARSE_LOG_FILE.equals(actionName)) {
            try {
                logFileParserDelegate.parseLogFile(request, response);
            } catch (TEAppException | FileNotFoundException e) {
                response.setHeader("Set-Cookie", "fileDownload=false; path=/");
                sendStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            }
        }
    }

    public void sendStatus(int statusCode, String error) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("error", error);
            response.setCharacterEncoding("UTF-8");
            response.setStatus(statusCode);
            PrintWriter writer = response.getWriter();
            writer.write(jsonObject.toString());
        } catch (JSONException | IOException e) {
            logger.error("sendStatus(): ", e);
        }
    }
}

