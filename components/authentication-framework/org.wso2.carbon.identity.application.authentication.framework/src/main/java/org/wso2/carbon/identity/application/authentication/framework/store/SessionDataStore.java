/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.application.authentication.framework.store;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.identity.application.common.IdentityApplicationManagementException;
import org.wso2.carbon.identity.base.IdentityException;
import org.wso2.carbon.identity.core.persistence.JDBCPersistenceManager;
import org.wso2.carbon.identity.core.util.IdentityDatabaseUtil;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.idp.mgt.util.IdPManagementUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Data will be persisted or stored date will be removed from the store. These two events are considered as STORE operation
 * and DELETE operations.
 * And these events are stored with unique sessionId, operation type and operation initiated timestamp.
 * After a DELETE operation is stored, STORE operations related to same sessionId and prior to initiated timestamp will
 * be removed from the store.
 * Expired DELETE operations and related STORE operations will be deleted by a OperationCleanUpService task.
 * All expired operations will be deleted by SessionCleanUpService task.
 *
 */
public class SessionDataStore {
    private static final Log log = LogFactory.getLog(SessionDataStore.class);

    private static final String OPERATION_DELETE = "DELETE";
    private static final String OPERATION_STORE = "STORE";
    private static final String SQL_INSERT_STORE_OPERATION =
            "INSERT INTO IDN_AUTH_SESSION_STORE(SESSION_ID, SESSION_TYPE, OPERATION, SESSION_OBJECT, TIME_CREATED) VALUES (?,?,?,?,?)";
    private static final String SQL_INSERT_DELETE_OPERATION =
            "INSERT INTO IDN_AUTH_SESSION_STORE(SESSION_ID, SESSION_TYPE,OPERATION, TIME_CREATED) VALUES (?,?,?,?)";
    private static final String SQL_DELETE_STORE_OPERATIONS_ON_DELETION =
            "DELETE FROM IDN_AUTH_SESSION_STORE WHERE SESSION_ID = ? AND SESSION_TYPE=? AND OPERATION=?";
    private static final String SQL_DELETE_STORE_OPERATIONS_TASK =
            "DELETE FROM IDN_AUTH_SESSION_STORE WHERE OPERATION = '"+OPERATION_STORE+"' AND SESSION_ID in (" +
            "SELECT SESSION_ID  FROM IDN_AUTH_SESSION_STORE WHERE OPERATION = '"+OPERATION_DELETE+"' AND TIME_CREATED < ?)";
    private static final String SQL_DELETE_DELETE_OPERATIONS_TASK =
            "DELETE FROM IDN_AUTH_SESSION_STORE WHERE OPERATION = '"+OPERATION_DELETE+"' AND  TIME_CREATED < ?";
    private static final String SQL_DESERIALIZE_OBJECT =
            "SELECT OPERATION, SESSION_OBJECT, TIME_CREATED FROM IDN_AUTH_SESSION_STORE WHERE SESSION_ID =? AND" +
            " SESSION_TYPE=? ORDER BY TIME_CREATED DESC LIMIT 1";
    private static final String SQL_DELETE_EXPIRED_DATA_TASK =
            "DELETE FROM IDN_AUTH_SESSION_STORE WHERE TIME_CREATED<?";

    private static int maxPoolSize = 100;
    private long operationCleanUpPeriod = 720;
    private static BlockingDeque<SessionContextDO> sessionContextQueue = new LinkedBlockingDeque();
    private static volatile SessionDataStore instance;
    private JDBCPersistenceManager jdbcPersistenceManager;
    private boolean enablePersist;
    private String sqlInsertSTORE;
    private String sqlInsertDELETE;
    private String sqlDeleteSTOREOnDELETE;
    private String sqlDeleteSTORETask;
    private String sqlDeleteDELETETask;
    private String sqlSelect;
    private String sqlDeleteExpiredDataTask;

    static {
        try {
            String maxPoolSizeConfigValue = IdentityUtil.getProperty("JDBCPersistenceManager.SessionDataPersist.PoolSize");
            if (StringUtils.isNotBlank(maxPoolSizeConfigValue)) {
                maxPoolSize = Integer.parseInt(maxPoolSizeConfigValue);
            }
        } catch (NumberFormatException e) {
            if (log.isDebugEnabled()) {
                log.debug("Exception ignored : ", e);
            }
            log.warn("Session data persistence pool size is not configured. Using default value.");
        }
        if (maxPoolSize > 0) {
            log.info("Thread pool size for session persistent consumer : " + maxPoolSize);

            ExecutorService threadPool = Executors.newFixedThreadPool(maxPoolSize);
            for (int i = 0; i < maxPoolSize; i++) {
                threadPool.execute(new SessionDataPersistTask(sessionContextQueue));
            }
        }
    }

    private SessionDataStore() {
        try {
            jdbcPersistenceManager = JDBCPersistenceManager.getInstance();

            String enablePersistVal = IdentityUtil.getProperty("JDBCPersistenceManager.SessionDataPersist.Enable");
            enablePersist = true;
            if (enablePersistVal != null) {
                enablePersist = Boolean.parseBoolean(enablePersistVal);
            }
            String insertSTORESQL = IdentityUtil
                    .getProperty("JDBCPersistenceManager.SessionDataPersist.SQL.InsertSTORE");
            String insertDELETESQL = IdentityUtil
                    .getProperty("JDBCPersistenceManager.SessionDataPersist.SQL.InsertDELETE");
            String deleteSTOREOnDELETESQL = IdentityUtil
                    .getProperty("JDBCPersistenceManager.SessionDataPersist.SQL.DeleteSTOREOnDELETE");
            String deleteSTORETaskSQL = IdentityUtil
                    .getProperty("JDBCPersistenceManager.SessionDataPersist.SQL.DeleteSTORETask");
            String deleteDELETETaskSQL = IdentityUtil
                    .getProperty("JDBCPersistenceManager.SessionDataPersist.SQL.DeleteDELETETask");
            String selectSQL = IdentityUtil
                    .getProperty("JDBCPersistenceManager.SessionDataPersist.SQL.Select");
            String deleteExpiredDataTaskSQL = IdentityUtil
                    .getProperty("JDBCPersistenceManager.SessionDataPersist.SQL.DeleteExpiredDataTask");
            if (!StringUtils.isBlank(insertSTORESQL)) {
                sqlInsertSTORE = insertSTORESQL;
            } else {
                sqlInsertSTORE = SQL_INSERT_STORE_OPERATION;
            }
            if (!StringUtils.isBlank(insertDELETESQL)) {
                sqlInsertDELETE = insertDELETESQL;
            } else {
                sqlInsertDELETE = SQL_INSERT_DELETE_OPERATION;
            }
            if (!StringUtils.isBlank(deleteSTOREOnDELETESQL)) {
                sqlDeleteSTOREOnDELETE = deleteSTOREOnDELETESQL;
            } else {
                sqlDeleteSTOREOnDELETE = SQL_DELETE_STORE_OPERATIONS_ON_DELETION;
            }
            if (!StringUtils.isBlank(deleteSTORETaskSQL)) {
                sqlDeleteSTORETask = deleteSTORETaskSQL;
            } else {
                sqlDeleteSTORETask = SQL_DELETE_STORE_OPERATIONS_TASK;
            }
            if (!StringUtils.isBlank(deleteDELETETaskSQL)) {
                sqlDeleteDELETETask = deleteDELETETaskSQL;
            } else {
                sqlDeleteDELETETask = SQL_DELETE_DELETE_OPERATIONS_TASK;
            }
            if (!StringUtils.isBlank(selectSQL)) {
                sqlSelect = selectSQL;
            } else {
                sqlSelect = SQL_DESERIALIZE_OBJECT;
            }
            if (!StringUtils.isBlank(deleteExpiredDataTaskSQL)) {
                sqlDeleteExpiredDataTask = deleteExpiredDataTaskSQL;
            } else {
                sqlDeleteExpiredDataTask = SQL_DELETE_EXPIRED_DATA_TASK;
            }
        } catch (IdentityException e) {
            //ignore
            log.error("Error while loading session data store manager", e);
        }
        if (!enablePersist) {
            log.info("Session Data Persistence of Authentication framework is not enabled.");
        }
        String isCleanUpEnabledVal = IdentityUtil.getProperty("JDBCPersistenceManager.SessionDataPersist.CleanUp.Enable");
        String isOperationCleanUpEnabledVal = IdentityUtil.getProperty("JDBCPersistenceManager.SessionDataOperations.CleanUp.Enable");
        String operationCleanUpPeriodVal = IdentityUtil.getProperty("JDBCPersistenceManager.SessionDataOperations.CleanUp.CleanUpPeriod");

        if (StringUtils.isBlank(isCleanUpEnabledVal)) {
            isCleanUpEnabledVal = "true";
        }
        if (StringUtils.isBlank(isOperationCleanUpEnabledVal)) {
            isOperationCleanUpEnabledVal = "true";
        }
        if (!StringUtils.isBlank(operationCleanUpPeriodVal)) {
            operationCleanUpPeriod = Long.parseLong(operationCleanUpPeriodVal);
        }
        if (Boolean.parseBoolean(isCleanUpEnabledVal)) {
            long sessionCleanupPeriod = IdPManagementUtil.getCleanUpPeriod(
                    CarbonContext.getThreadLocalCarbonContext().getTenantDomain());
            SessionCleanUpService sessionCleanUpService = new SessionCleanUpService(sessionCleanupPeriod, sessionCleanupPeriod);
            sessionCleanUpService.activateCleanUp();
        } else {
            log.info("Session Data CleanUp Task of Authentication framework is not enabled.");
        }
        if (Boolean.parseBoolean(isOperationCleanUpEnabledVal)) {
            OperationCleanUpService operationCleanUpService = new OperationCleanUpService(operationCleanUpPeriod, operationCleanUpPeriod);
            operationCleanUpService.activateCleanUp();
        } else {
            log.info("Session Data Operations CleanUp Task of Authentication framework is not enabled.");
        }
    }
    
    public static SessionDataStore getInstance() {
        if (instance == null) {
            synchronized (SessionDataStore.class) {
                if (instance == null) {
                    instance = new SessionDataStore();
                }
            }
        }
        return instance;
    }

    public Object getSessionData(String key, String type) {
        SessionContextDO sessionContextDO = getSessionContextData(key, type);
        return sessionContextDO != null ? sessionContextDO.getEntry() : null;
    }

    public SessionContextDO getSessionContextData(String key, String type) {
        if (!enablePersist) {
            return null;
        }
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            connection = jdbcPersistenceManager.getDBConnection();
            preparedStatement = connection.prepareStatement(sqlSelect);
            preparedStatement.setString(1, key);
            preparedStatement.setString(2, type);
            resultSet = preparedStatement.executeQuery();
            if ((resultSet.next()) &&
                (OPERATION_STORE.equals(resultSet.getString(1)))) {
                return new SessionContextDO(key, type, getBlobObject(resultSet.getBinaryStream(2)), resultSet.getTimestamp(3));
            }
        } catch (SQLException | IdentityException | ClassNotFoundException | IOException | IdentityApplicationManagementException e) {
            log.error("Error while retrieving session data", e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, resultSet, preparedStatement);
        }
        return null;
    }

    public void storeSessionData(String key, String type, Object entry) {
        if (!enablePersist) {
            return;
        }
        Timestamp timestamp = new Timestamp(new Date().getTime());
        if (maxPoolSize > 0) {
            sessionContextQueue.push(new SessionContextDO(key, type, entry, timestamp));
        } else {
            persistSessionData(key, type, entry, timestamp);
        }
    }

    public void clearSessionData(String key, String type) {
        if (!enablePersist) {
            return;
        }
        Timestamp timestamp = new Timestamp(new Date().getTime());
        if (maxPoolSize > 0) {
            sessionContextQueue.push(new SessionContextDO(key, type, null, timestamp));
        } else {
            removeSessionData(key, type, timestamp);
        }
    }

    public void removeExpiredSessionData(Timestamp timestamp) {
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = jdbcPersistenceManager.getDBConnection();
            statement = connection.prepareStatement(sqlDeleteExpiredDataTask);
            statement.setTimestamp(1, timestamp);
            statement.execute();
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException e) {
            log.error("Error while removing session data from the database for the timestamp " + timestamp.toString(), e);
        } catch (IdentityException e) {
            log.error("Error while obtaining the database connection", e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, statement);

        }
    }

    public void removeExpiredOperationData(Timestamp timestamp) {
        deleteSTOREOperationsTask(timestamp);
        deleteDELETEOperationsTask(timestamp);
    }

    public void persistSessionData(String key, String type, Object entry, Timestamp timestamp) {
        if (!enablePersist) {
            return;
        }
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            connection = jdbcPersistenceManager.getDBConnection();
            preparedStatement = connection.prepareStatement(sqlInsertSTORE);
            preparedStatement.setString(1, key);
            preparedStatement.setString(2, type);
            preparedStatement.setString(3, OPERATION_STORE);
            setBlobObject(preparedStatement, entry, 4);
            preparedStatement.setTimestamp(5, timestamp);
            preparedStatement.executeUpdate();
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (IdentityException | SQLException | IOException e) {
            log.error("Error while storing session data", e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, resultSet, preparedStatement);
        }
    }

    public void removeSessionData(String key, String type, Timestamp timestamp) {
        if (!enablePersist) {
            return;
        }
        storeDELETEOperations(key, type, timestamp);
        deleteSTOREOperations(key, type);
    }

    private void setBlobObject(PreparedStatement prepStmt, Object value, int index)
            throws SQLException, IOException {
        if (value != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(value);
            oos.flush();
            oos.close();
            InputStream inputStream = new ByteArrayInputStream(baos.toByteArray());
            prepStmt.setBinaryStream(index, inputStream, inputStream.available());
        } else {
            prepStmt.setBinaryStream(index, null, 0);
        }
    }

    private Object getBlobObject(InputStream is)
            throws IdentityApplicationManagementException, IOException, ClassNotFoundException {
        if (is != null) {
            ObjectInput ois = null;
            try {
                ois = new ObjectInputStream(is);
                return ois.readObject();
            } finally {
                if (ois != null) {
                    try {
                        ois.close();
                    } catch (IOException e) {
                        log.error("IOException while trying to close ObjectInputStream.", e);
                    }
                }
            }
        }
        return null;
    }

    private void storeDELETEOperations(String key, String type, Timestamp timestamp) {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = jdbcPersistenceManager.getDBConnection();
            preparedStatement = connection.prepareStatement(sqlInsertDELETE);
            preparedStatement.setString(1, key);
            preparedStatement.setString(2, type);
            preparedStatement.setString(3, OPERATION_DELETE);
            preparedStatement.setTimestamp(4, timestamp);
            preparedStatement.executeUpdate();
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (Exception e) {
            log.error("Error while storing DELETE operation session data", e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, preparedStatement);
        }
    }

    private void deleteSTOREOperations(String key, String type) {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = jdbcPersistenceManager.getDBConnection();
            preparedStatement = connection.prepareStatement(sqlDeleteSTOREOnDELETE);
            preparedStatement.setString(1, key);
            preparedStatement.setString(2, type);
            preparedStatement.setString(3, OPERATION_STORE);
            preparedStatement.executeUpdate();
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (Exception e) {
            log.error("Error while deleting STORE operations on DELETE operation data", e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, preparedStatement);
        }

    }

    private void deleteSTOREOperationsTask(Timestamp timestamp) {
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = jdbcPersistenceManager.getDBConnection();
            statement = connection.prepareStatement(sqlDeleteSTORETask);
            statement.setTimestamp(1, timestamp);
            statement.execute();
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
            return;
        } catch (SQLException e) {
            log.error("Error while removing STORE operation data from the database for the timestamp " + timestamp.toString(), e);
        } catch (IdentityException e) {
            log.error("Error while obtaining the database connection", e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, statement);

        }

    }

    private void deleteDELETEOperationsTask(Timestamp timestamp) {
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = jdbcPersistenceManager.getDBConnection();
            statement = connection.prepareStatement(sqlDeleteDELETETask);
            statement.setTimestamp(1, timestamp);
            statement.execute();
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
            return;
        } catch (SQLException e) {
            log.error("Error while removing DELETE operation data from the database for the timestamp " + timestamp.toString(), e);
        } catch (IdentityException e) {
            log.error("Error while obtaining the database connection", e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, statement);

        }
    }
}
