/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.identity.mgt.store;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RegistryCleanUpService {

    private static final int NUM_THREADS = 1;
    private static final Log log = LogFactory.getLog(RegistryCleanUpService.class);
    private final ScheduledExecutorService scheduler;
    private final long initialDelay;
    private final long delayBetweenRuns;

    /**
     * @param initialDelay
     * @param delayBetweenRuns
     */
    public RegistryCleanUpService(long initialDelay, long delayBetweenRuns) {
        this.initialDelay = initialDelay;
        this.delayBetweenRuns = delayBetweenRuns;
        this.scheduler = Executors.newScheduledThreadPool(NUM_THREADS);
    }

    /**
     *
     */
    public void activateCleanUp() {
        Runnable registryCleanUpTask = new RegistryCleanUpTask();
        scheduler.scheduleWithFixedDelay(registryCleanUpTask, initialDelay, delayBetweenRuns,
                TimeUnit.MINUTES);

    }

    /**
     *
     *
     */
    private static final class RegistryCleanUpTask implements Runnable {

        @Override
        public void run() {

            log.debug("Start running the Identity-Management registry Data cleanup task.");
            Date date = new Date();
            log.error("Running-------------------------------------");
//            int sessionTimeout = IdPManagementUtil.getMaxCleanUpTimeout();
//            Timestamp timestamp = new Timestamp((date.getTime() - (sessionTimeout * 60 * 1000)));
//            SessionDataStore.getInstance().removeExpiredSessionData(timestamp);
            log.debug("Stop running the Identity-Management registry Data cleanup task.");
            log.info("Identity-Management registry Data cleanup task finished for removing expired Data");
        }
    }

}
