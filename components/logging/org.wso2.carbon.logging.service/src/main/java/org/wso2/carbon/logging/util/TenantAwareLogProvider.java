/*
*  Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.logging.util;

import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.wso2.carbon.base.ServerConfiguration;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.logging.api.ILogProvider;
import org.wso2.carbon.logging.appender.CarbonMemoryAppender;
import org.wso2.carbon.logging.service.LogViewerException;
import org.wso2.carbon.logging.service.data.LogEvent;
import org.wso2.carbon.logging.service.data.LoggingConfig;
import org.wso2.carbon.utils.logging.TenantAwareLoggingEvent;
import org.wso2.carbon.utils.logging.TenantAwarePatternLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Created by shameera on 6/26/14.
 */
public class TenantAwareLogProvider implements ILogProvider {
    /**
     * Initialize the log provider by reading the property comes with logging configuration file
     * This will be called immediate after create new instance of ILogProvider
     *
     * @param loggingConfig
     */
    @Override
    public void init(LoggingConfig loggingConfig) {
       // nothing to do
    }

    @Override
    public String[] getApplicationNames(String domain, String serverKey) throws LogViewerException {
        List<String> appList = new ArrayList<String>();
        LogEvent allLogs[] = getLogsByAppName("", domain, serverKey);
        for (LogEvent event : allLogs) {
            if (event.getAppName() != null && !event.getAppName().equals("")
                    && !event.getAppName().equals("NA")
                    && !LoggingUtil.isAdmingService(event.getAppName())
                    && !appList.contains(event.getAppName())
                    && !event.getAppName().equals("STRATOS_ROOT")) {
                appList.add(event.getAppName());
            }
        }
        return getSortedApplicationNames(appList);
    }


    @Override
    public LogEvent[] getSystemLogs() throws LogViewerException {
        int DEFAULT_NO_OF_LOGS = 100;
        int definedAmount;
        Appender appender = Logger.getRootLogger().getAppender(
                LoggingConstants.WSO2CARBON_MEMORY_APPENDER);
        if (appender instanceof CarbonMemoryAppender) {
            CarbonMemoryAppender memoryAppender = (CarbonMemoryAppender) appender;
            if ((memoryAppender.getCircularQueue() != null)) {
                definedAmount = memoryAppender.getBufferSize();
            } else {
                return null;
            }

            Object[] objects;
            if (definedAmount < 1) {
                objects = memoryAppender.getCircularQueue().getObjects(DEFAULT_NO_OF_LOGS);
            } else {
                objects = memoryAppender.getCircularQueue().getObjects(definedAmount);
            }
            if ((memoryAppender.getCircularQueue().getObjects(definedAmount) == null)
                    || (memoryAppender.getCircularQueue().getObjects(definedAmount).length == 0)) {
                return null;
            }
            List<LogEvent> resultList = new ArrayList<LogEvent>();
            for (int i = 0; i < objects.length; i++) {
                TenantAwareLoggingEvent logEvt = (TenantAwareLoggingEvent) objects[i];
                if (logEvt != null) {
                    resultList.add(createLogEvent(logEvt));
                }
            }
            if (resultList.isEmpty()) {
                return null;
            }
            List<LogEvent> reverseList = reverseLogList(resultList);
            return reverseList.toArray(new LogEvent[reverseList.size()]);
        } else {
            return new LogEvent[] { new LogEvent(
                    "The log must be configured to use the "
                            + "org.wso2.carbon.logging.core.util.MemoryAppender to view entries through the admin console",
                    "") };
        }
    }

    @Override
    public LogEvent[] getAllLogs(String domain, String serverKey) throws LogViewerException {
        return new LogEvent[0];
    }

    @Override
    public LogEvent[] getLogsByAppName(String appName, String domain, String serverKey) throws LogViewerException {
        int DEFAULT_NO_OF_LOGS = 100;
        int definedamount;
        Appender appender = Logger.getRootLogger().getAppender(
                LoggingConstants.WSO2CARBON_MEMORY_APPENDER);
        if (appender instanceof CarbonMemoryAppender) {
            CarbonMemoryAppender memoryAppender = (CarbonMemoryAppender) appender;
            if ((memoryAppender.getCircularQueue() != null)) {
                definedamount = memoryAppender.getBufferSize();
            } else {
                return null;
            }

            Object[] objects;
            if (definedamount < 1) {
                objects = memoryAppender.getCircularQueue().getObjects(DEFAULT_NO_OF_LOGS);
            } else {
                objects = memoryAppender.getCircularQueue().getObjects(definedamount);
            }
            if ((memoryAppender.getCircularQueue().getObjects(definedamount) == null)
                    || (memoryAppender.getCircularQueue().getObjects(definedamount).length == 0)) {
                return null;
            }
            List<LogEvent> resultList = new ArrayList<LogEvent>();
            for (int i = 0; i < objects.length; i++) {
                TenantAwareLoggingEvent logEvt = (TenantAwareLoggingEvent) objects[i];
                if (logEvt != null) {
                    TenantAwarePatternLayout tenantIdPattern = new TenantAwarePatternLayout("%T");
                    TenantAwarePatternLayout productPattern = new TenantAwarePatternLayout("%S");
                    String productName = productPattern.format(logEvt);
                    String tenantId = tenantIdPattern.format(logEvt);
                    if (isCurrentTenantId(tenantId, domain) && isCurrentProduct(productName, serverKey)) {
                        if (appName == null || appName.equals("")) {
                            resultList.add(createLogEvent(logEvt));
                        } else {
                            TenantAwarePatternLayout appPattern = new TenantAwarePatternLayout("%A");
                            String currAppName = appPattern.format(logEvt);
                            if (appName.equals(currAppName)) {
                                resultList.add(createLogEvent(logEvt));
                            }
                        }
                    }
                }
            }
            List<LogEvent> reverseList = reverseLogList(resultList);
            return reverseList.toArray(new LogEvent[reverseList.size()]);
        } else {
            return new LogEvent[] { new LogEvent(
                    "The log must be configured to use the org.wso2.carbon."
                            + "logging.core.util.MemoryAppender to view entries on the admin console",
                    "NA") };
        }
    }

    @Override
    public LogEvent[] getLogs(String type, String keyword,String appName, String domain, String serverKey) throws LogViewerException {
        if (keyword == null || keyword.equals("")) {
            // keyword is null
            if (type == null || type.equals("") || type.equalsIgnoreCase("ALL")) {
                return this.getLogs("", domain, serverKey);
            } else {
                // type is NOT null and NOT equal to ALL Application Name is not
                // needed
                return this.getLogsForType(type, "", domain, serverKey);
            }
        } else {
            // keyword is NOT null
            if (type == null || type.equals("")) {
                // type is null
                return this.getLogsForKey(keyword, "", domain, serverKey);
            } else {
                // type is NOT null and keyword is NOT null, but type can be
                // equal to ALL
                if ("ALL".equalsIgnoreCase(type)) {
                    return getLogsForKey(keyword, appName, domain, serverKey);
                } else {
                    LogEvent[] filerByType = getLogsForType(type, appName, domain, serverKey);
                    List<LogEvent> resultList = new ArrayList<LogEvent>();
                    if (filerByType != null) {
                        for (int i = 0; i < filerByType.length; i++) {
                            String logMessage = filerByType[i].getMessage();
                            String logger = filerByType[i].getLogger();
                            if (logMessage != null
                                    && logMessage.toLowerCase().indexOf(keyword.toLowerCase()) > -1) {
                                resultList.add(filerByType[i]);
                            } else if (logger != null
                                    && logger.toLowerCase().indexOf(keyword.toLowerCase()) > -1) {
                                resultList.add(filerByType[i]);
                            }
                        }
                    }
                    if (resultList.isEmpty()) {
                        return null;
                    }
                    List<LogEvent> reverseList = reverseLogList(resultList);
                    return reverseList.toArray(new LogEvent[reverseList.size()]);
                }
            }
        }



    }

    private LogEvent[] getLogs(String appName, String domain, String serverKey) {
        int DEFAULT_NO_OF_LOGS = 100;
        int definedamount;
        Appender appender = Logger.getRootLogger().getAppender(
                LoggingConstants.WSO2CARBON_MEMORY_APPENDER);
        if (appender instanceof CarbonMemoryAppender) {
            CarbonMemoryAppender memoryAppender = (CarbonMemoryAppender) appender;
            if ((memoryAppender.getCircularQueue() != null)) {
                definedamount = memoryAppender.getBufferSize();
            } else {
                return null;
            }

            Object[] objects;
            if (definedamount < 1) {
                objects = memoryAppender.getCircularQueue().getObjects(DEFAULT_NO_OF_LOGS);
            } else {
                objects = memoryAppender.getCircularQueue().getObjects(definedamount);
            }
            if ((memoryAppender.getCircularQueue().getObjects(definedamount) == null)
                    || (memoryAppender.getCircularQueue().getObjects(definedamount).length == 0)) {
                return null;
            }
            List<LogEvent> resultList = new ArrayList<LogEvent>();
            for (int i = 0; i < objects.length; i++) {
                TenantAwareLoggingEvent logEvt = (TenantAwareLoggingEvent) objects[i];
                if (logEvt != null) {
                    TenantAwarePatternLayout tenantIdPattern = new TenantAwarePatternLayout("%T");
                    TenantAwarePatternLayout productPattern = new TenantAwarePatternLayout("%S");
                    String productName = productPattern.format(logEvt);
                    String tenantId = tenantIdPattern.format(logEvt);
                    if (isCurrentTenantId(tenantId, domain) && isCurrentProduct(productName, serverKey)) {
                        if (appName == null || appName.equals("")) {
                            resultList.add(createLogEvent(logEvt));
                        } else {
                            TenantAwarePatternLayout appPattern = new TenantAwarePatternLayout("%A");
                            String currAppName = appPattern.format(logEvt);
                            if (appName.equals(currAppName)) {
                                resultList.add(createLogEvent(logEvt));
                            }
                        }
                    }
                }
            }
            List<LogEvent> reverseList = reverseLogList(resultList);
            return reverseList.toArray(new LogEvent[reverseList.size()]);
        } else {
            return new LogEvent[] { new LogEvent(
                    "The log must be configured to use the org.wso2.carbon."
                            + "logging.core.util.MemoryAppender to view entries on the admin console",
                    "NA") };
        }
    }

    @Override
    public int logsCount(String domain, String serverKey) throws LogViewerException {
        return 0;
    }

    @Override
    public boolean clearLogs() {
        return false;
    }

    private boolean isCurrentTenantId(String tenantId, String domain) {
        String currTenantId;
        if (domain.equals("")) {
            currTenantId = String.valueOf(CarbonContext.getCurrentContext().getTenantId());
            return currTenantId.equals(tenantId);
        }else {
            try {
                currTenantId = String.valueOf(LoggingUtil.getTenantIdForDomain(domain));
                return currTenantId.equals(tenantId);
            } catch (LogViewerException e) {

            }
        }
        return false;
    }

    private boolean isCurrentProduct(String productName, String serverKey) {
        if(serverKey.equals("")) {
            String currProductName = ServerConfiguration.getInstance().getFirstProperty("ServerKey");
            return currProductName.equals(productName);
        } else {
            return productName.equals(serverKey);
        }

    }

    private LogEvent createLogEvent(TenantAwareLoggingEvent logEvt) {
        Appender appender = Logger.getRootLogger().getAppender(
                LoggingConstants.WSO2CARBON_MEMORY_APPENDER);
        CarbonMemoryAppender memoryAppender = (CarbonMemoryAppender) appender;
        List<String> patterns = Arrays.asList(memoryAppender.getColumnList().split(","));
        String tenantID = null;
        String serverName = null;
        String appName = null;
        String logTime = null;
        String logger = null;
        String priority = null;
        String message = null;
        String stacktrace = null;
        String ip = null;
        String instance = null;
        for (Iterator<String> j = patterns.iterator(); j.hasNext();) {
            String currEle = ((String) j.next()).replace("%", "");
            TenantAwarePatternLayout patternLayout = new TenantAwarePatternLayout("%" + currEle);
            if (currEle.equals("T")) {
                tenantID = patternLayout.format(logEvt);
                continue;
            }
            if (currEle.equals("S")) {
                serverName = patternLayout.format(logEvt);
                continue;
            }
            if (currEle.equals("A")) {
                appName = patternLayout.format(logEvt);
                continue;
            }
            if (currEle.equals("d")) {
                logTime = patternLayout.format(logEvt);
                continue;
            }
            if (currEle.equals("c")) {
                logger = patternLayout.format(logEvt);
                continue;
            }
            if (currEle.equals("p")) {
                priority = patternLayout.format(logEvt);
                continue;
            }
            if (currEle.equals("m")) {
                message = patternLayout.format(logEvt);
                continue;
            }
            if (currEle.equals("I")) {
                instance = patternLayout.format(logEvt);
                continue;
            }
            if (currEle.equals("Stacktrace")) {
                if (logEvt.getThrowableInformation() != null) {
                    stacktrace = getStacktrace(logEvt.getThrowableInformation().getThrowable());
                } else {
                    stacktrace = "";
                }
                continue;
            }
            if (currEle.equals("H")) {
                ip = patternLayout.format(logEvt);
                continue;
            }
        }
        return new LogEvent(tenantID, serverName, appName, logTime, logger, priority, message, ip,
                stacktrace, instance);
    }

    private String getStacktrace(Throwable e) {
        StringBuilder stackTrace = new StringBuilder();
        StackTraceElement[] stackTraceElements = e.getStackTrace();
        for (StackTraceElement ele : stackTraceElements) {
            stackTrace.append(ele.toString()).append("\n");
        }
        return stackTrace.toString();
    }

    private ArrayList<LogEvent> reverseLogList(List<LogEvent> resultList) {
        ArrayList<LogEvent> reverseList = new ArrayList<LogEvent>(resultList.size());
        for (int i = resultList.size() - 1; i >= 0; i--) {
            reverseList.add(resultList.get(i));
        }
        return reverseList;
    }

    private LogEvent[] getLogsForKey(String keyword, String appName, String domain, String serviceKey) {
        int DEFAULT_NO_OF_LOGS = 100;
        int definedAmount;
        Appender appender = Logger.getRootLogger().getAppender(
                LoggingConstants.WSO2CARBON_MEMORY_APPENDER);
        if (appender instanceof CarbonMemoryAppender) {
            CarbonMemoryAppender memoryAppender = (CarbonMemoryAppender) appender;
            if ((memoryAppender.getCircularQueue() != null)) {
                definedAmount = memoryAppender.getBufferSize();
            } else {
                return null;
            }
            Object[] objects;
            if (definedAmount < 1) {
                objects = memoryAppender.getCircularQueue().getObjects(DEFAULT_NO_OF_LOGS);
            } else {
                objects = memoryAppender.getCircularQueue().getObjects(definedAmount);
            }
            if ((memoryAppender.getCircularQueue().getObjects(definedAmount) == null)
                    || (memoryAppender.getCircularQueue().getObjects(definedAmount).length == 0)) {
                return null;
            }
            List<LogEvent> resultList = new ArrayList<LogEvent>();
            for (int i = 0; i < objects.length; i++) {
                TenantAwareLoggingEvent logEvt = (TenantAwareLoggingEvent) objects[i];
                if (logEvt != null) {
                    TenantAwarePatternLayout tenantIdPattern = new TenantAwarePatternLayout("%T");
                    TenantAwarePatternLayout productPattern = new TenantAwarePatternLayout("%S");
                    TenantAwarePatternLayout messagePattern = new TenantAwarePatternLayout("%m");
                    TenantAwarePatternLayout loggerPattern = new TenantAwarePatternLayout("%c");
                    String productName = productPattern.format(logEvt);
                    String tenantId = tenantIdPattern.format(logEvt);
                    String result = messagePattern.format(logEvt);
                    String logger = loggerPattern.format(logEvt);
                    boolean isInLogMessage = result != null
                            && (result.toLowerCase().indexOf(keyword.toLowerCase()) > -1);
                    boolean isInLogger = logger != null
                            && (logger.toLowerCase().indexOf(keyword.toLowerCase()) > -1);
                    if (isCurrentTenantId(tenantId, domain) && isCurrentProduct(productName, serviceKey)
                            && (isInLogMessage || isInLogger)) {
                        if (appName == null || appName.equals("")) {
                            resultList.add(createLogEvent(logEvt));
                        } else {
                            TenantAwarePatternLayout appPattern = new TenantAwarePatternLayout("%A");
                            String currAppName = appPattern.format(logEvt);
                            if (appName.equals(currAppName)) {
                                resultList.add(createLogEvent(logEvt));
                            }
                        }
                    }
                }
            }
            if (resultList.isEmpty()) {
                return null;
            }
            List<LogEvent> reverseList = reverseLogList(resultList);
            return reverseList.toArray(new LogEvent[reverseList.size()]);
        } else {
            return new LogEvent[] { new LogEvent(
                    "The log must be configured to use the "
                            + "org.wso2.carbon.logging.core.util.MemoryAppender to view entries on the admin console",
                    "NA") };
        }
    }

    private LogEvent[] getLogsForType(String type, String appName, String domain, String serviceName) {
        int DEFAULT_NO_OF_LOGS = 100;
        int definedAmount;
        Appender appender = Logger.getRootLogger().getAppender(
                LoggingConstants.WSO2CARBON_MEMORY_APPENDER);
        if (appender instanceof CarbonMemoryAppender) {
            CarbonMemoryAppender memoryAppender = (CarbonMemoryAppender) appender;
            if ((memoryAppender.getCircularQueue() != null)) {
                definedAmount = memoryAppender.getBufferSize();
            } else {
                return null;
            }

            Object[] objects;
            if (definedAmount < 1) {
                objects = memoryAppender.getCircularQueue().getObjects(DEFAULT_NO_OF_LOGS);
            } else {
                objects = memoryAppender.getCircularQueue().getObjects(definedAmount);
            }
            if ((memoryAppender.getCircularQueue().getObjects(definedAmount) == null)
                    || (memoryAppender.getCircularQueue().getObjects(definedAmount).length == 0)) {
                return null;
            }
            List<LogEvent> resultList = new ArrayList<LogEvent>();
            for (int i = 0; i < objects.length; i++) {
                TenantAwareLoggingEvent logEvt = (TenantAwareLoggingEvent) objects[i];
                if (logEvt != null) {
                    TenantAwarePatternLayout tenantIdPattern = new TenantAwarePatternLayout("%T");
                    TenantAwarePatternLayout productPattern = new TenantAwarePatternLayout("%S");
                    String priority = logEvt.getLevel().toString();
                    String productName = productPattern.format(logEvt);
                    String tenantId = tenantIdPattern.format(logEvt);
                    if ((priority.toString().equals(type) && isCurrentTenantId(tenantId, domain) && isCurrentProduct(productName, serviceName))) {
                        if (appName == null || appName.equals("")) {
                            resultList.add(createLogEvent(logEvt));
                        } else {
                            TenantAwarePatternLayout appPattern = new TenantAwarePatternLayout("%A");
                            String currAppName = appPattern.format(logEvt);
                            if (appName.equals(currAppName)) {
                                resultList.add(createLogEvent(logEvt));
                            }
                        }
                    }
                }
            }
            if (resultList.isEmpty()) {
                return null;
            }
            List<LogEvent> reverseList = reverseLogList(resultList);
            return reverseList.toArray(new LogEvent[reverseList.size()]);
        } else {
            return new LogEvent[] { new LogEvent(
                    "The log must be configured to use the "
                            + "org.wso2.carbon.logging.core.util.MemoryAppender to view entries through the admin console",
                    "") };
        }
    }

    private  String[] getSortedApplicationNames(List<String> applicationNames) {
        Collections.sort(applicationNames, new Comparator<String>() {
            public int compare(String s1, String s2) {
                return s1.toLowerCase().compareTo(s2.toLowerCase());
            }

        });
        return (String[]) applicationNames.toArray(new String[applicationNames.size()]);
    }

}
