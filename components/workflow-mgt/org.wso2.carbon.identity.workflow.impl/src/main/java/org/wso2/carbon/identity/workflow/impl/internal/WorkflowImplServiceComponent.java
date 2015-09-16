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

package org.wso2.carbon.identity.workflow.impl.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.base.ServerConfiguration;
import org.wso2.carbon.identity.core.util.IdentityCoreConstants;
import org.wso2.carbon.identity.workflow.impl.WFImplConstant;
import org.wso2.carbon.identity.workflow.impl.WorkflowImplService;
import org.wso2.carbon.identity.workflow.impl.bean.BPSProfile;
import org.wso2.carbon.identity.workflow.impl.ApprovalWorkflow;
import org.wso2.carbon.identity.workflow.impl.listener.WorkflowImplTenantMgtListener;
import org.wso2.carbon.identity.workflow.mgt.exception.WorkflowException;
import org.wso2.carbon.identity.workflow.mgt.exception.WorkflowRuntimeException;
import org.wso2.carbon.identity.workflow.mgt.internal.WorkflowServiceDataHolder;
import org.wso2.carbon.identity.workflow.mgt.util.WFConstant;
import org.wso2.carbon.identity.workflow.mgt.util.WorkflowManagementUtil;
import org.wso2.carbon.identity.workflow.mgt.workflow.AbstractWorkflow;
import org.wso2.carbon.stratos.common.listeners.TenantMgtListener;
import org.wso2.carbon.utils.NetworkUtils;

import java.io.IOException;
import java.net.SocketException;
import java.net.URISyntaxException;

/**
 * @scr.component name="identity.workflow.bpel" immediate="true"
 */
public class WorkflowImplServiceComponent {

    private static Log log = LogFactory.getLog(WorkflowImplServiceComponent.class);


    protected void activate(ComponentContext context) {

        BundleContext bundleContext = context.getBundleContext();

        String metaDataXML = readWorkflowImplParamMetaDataXML();
        bundleContext.registerService(AbstractWorkflow.class, new ApprovalWorkflow(metaDataXML), null);


        WorkflowImplTenantMgtListener workflowTenantMgtListener = new WorkflowImplTenantMgtListener();
        ServiceRegistration tenantMgtListenerSR = bundleContext.registerService(
                TenantMgtListener.class.getName(), workflowTenantMgtListener, null);
        if (tenantMgtListenerSR != null) {
            log.debug("Workflow Management - WorkflowTenantMgtListener registered");
        } else {
            log.error("Workflow Management - WorkflowTenantMgtListener could not be registered");
        }

        this.addDefaultBPSProfile();

    }


    private void addDefaultBPSProfile() {

        BPSProfile bpsProfileDTO = new BPSProfile();
        String hostName = ServerConfiguration.getInstance().getFirstProperty(IdentityCoreConstants.HOST_NAME);
        String offset = ServerConfiguration.getInstance().getFirstProperty(IdentityCoreConstants.PORTS_OFFSET);
        String userName = WorkflowServiceDataHolder.getInstance().getRealmService().getBootstrapRealmConfiguration()
                .getAdminUserName();
        String password = WorkflowServiceDataHolder.getInstance().getRealmService().getBootstrapRealmConfiguration()
                .getAdminPassword();
        try {
            if (hostName == null) {
                hostName = NetworkUtils.getLocalHostname();
            }
            String url = "https://" + hostName + ":" + (9443 + Integer.parseInt(offset));

            bpsProfileDTO.setHost(url);
            bpsProfileDTO.setUsername(userName);
            bpsProfileDTO.setPassword(password);
            bpsProfileDTO.setCallbackUser(userName);
            bpsProfileDTO.setCallbackPassword(password);
            bpsProfileDTO.setProfileName(WFConstant.DEFAULT_BPS_PROFILE);

            WorkflowImplService workflowImplService = WorkflowImplServiceDataHolder.getInstance().getWorkflowImplService();
            BPSProfile currentBpsProfile = workflowImplService.getBPSProfile(WFConstant.DEFAULT_BPS_PROFILE,
                                                                        MultitenantConstants.SUPER_TENANT_ID);
            if (currentBpsProfile == null) {
                workflowImplService.addBPSProfile(bpsProfileDTO, MultitenantConstants.SUPER_TENANT_ID);
                if (log.isDebugEnabled()) {
                    log.info("Default BPS profile added to the DB");
                }
            }
        } catch (SocketException e) {
            //This is not thrown exception because this is not blocked to the other functionality. User can create
            // default profile by manually.
            String errorMsg = "Error while trying to read hostname, " + e.getMessage();
            log.error(errorMsg);
        } catch (WorkflowException e) {
            //This is not thrown exception because this is not blocked to the other functionality. User can create
            // default profile by manually.
            String errorMsg = "Error occured while adding default bps profile, " + e.getMessage();
            log.error(errorMsg);
        }
    }

    private String readWorkflowImplParamMetaDataXML() throws WorkflowRuntimeException {
        String content = null ;
        try {
            content = WorkflowManagementUtil.readFileFromResource(WFImplConstant.WORKFLOW_IMPL_PARAMETER_METADATA_FILE_NAME);
        } catch (URISyntaxException e) {
            String errorMsg = "Error occurred while reading file from class path, " + e.getMessage() ;
            log.error(errorMsg);
            throw new WorkflowRuntimeException(errorMsg,e);
        } catch (IOException e) {
            String errorMsg = "Error occurred while reading file from class path, " + e.getMessage() ;
            log.error(errorMsg);
            throw new WorkflowRuntimeException(errorMsg,e);
        }
        return content ;
    }


}
