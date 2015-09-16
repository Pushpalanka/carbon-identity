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

package org.wso2.carbon.user.mgt.workflow.internal;

import org.osgi.framework.BundleContext;
import org.wso2.carbon.identity.workflow.mgt.WorkflowManagementServiceImpl;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.ConfigurationContextService;

public class IdentityWorkflowDataHolder {

    private static IdentityWorkflowDataHolder instance = new IdentityWorkflowDataHolder();

    private RealmService realmService;
    private ConfigurationContextService configurationContextService;
    private BundleContext bundleContext;
    private WorkflowManagementServiceImpl workflowService;

    public static IdentityWorkflowDataHolder getInstance() {

        return instance;
    }

    private IdentityWorkflowDataHolder() {

    }

    public RealmService getRealmService() {

        return realmService;
    }

    public void setRealmService(RealmService realmService) {

        this.realmService = realmService;
    }

    public ConfigurationContextService getConfigurationContextService() {

        return configurationContextService;
    }

    public void setConfigurationContextService(
            ConfigurationContextService configurationContextService) {

        this.configurationContextService = configurationContextService;
    }

    public BundleContext getBundleContext() {

        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {

        this.bundleContext = bundleContext;
    }

    public WorkflowManagementServiceImpl getWorkflowService() {

        return workflowService;
    }

    public void setWorkflowService(WorkflowManagementServiceImpl workflowService) {

        this.workflowService = workflowService;
    }
}
