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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.application.mgt;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.rahas.impl.SAMLTokenIssuerConfig;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.context.RegistryType;
import org.wso2.carbon.core.RegistryResources;
import org.wso2.carbon.identity.application.common.IdentityApplicationManagementException;
import org.wso2.carbon.identity.application.common.model.ApplicationBasicInfo;
import org.wso2.carbon.identity.application.common.model.ApplicationPermission;
import org.wso2.carbon.identity.application.common.model.AuthenticationStep;
import org.wso2.carbon.identity.application.common.model.IdentityProvider;
import org.wso2.carbon.identity.application.common.model.InboundAuthenticationRequestConfig;
import org.wso2.carbon.identity.application.common.model.LocalAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.PermissionsAndRoleConfig;
import org.wso2.carbon.identity.application.common.model.RequestPathAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.common.util.IdentityApplicationConstants;
import org.wso2.carbon.identity.application.mgt.cache.IdentityServiceProviderCache;
import org.wso2.carbon.identity.application.mgt.cache.IdentityServiceProviderCacheEntry;
import org.wso2.carbon.identity.application.mgt.cache.IdentityServiceProviderCacheKey;
import org.wso2.carbon.identity.application.mgt.dao.ApplicationDAO;
import org.wso2.carbon.identity.application.mgt.dao.IdentityProviderDAO;
import org.wso2.carbon.identity.application.mgt.dao.OAuthApplicationDAO;
import org.wso2.carbon.identity.application.mgt.dao.SAMLApplicationDAO;
import org.wso2.carbon.identity.application.mgt.dao.impl.FileBasedApplicationDAO;
import org.wso2.carbon.identity.application.mgt.internal.ApplicationManagementServiceComponent;
import org.wso2.carbon.identity.application.mgt.internal.ApplicationManagementServiceComponentHolder;
import org.wso2.carbon.identity.application.mgt.internal.ApplicationMgtListenerServiceComponent;
import org.wso2.carbon.identity.application.mgt.listener.ApplicationMgtListener;
import org.wso2.carbon.registry.api.RegistryException;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.security.SecurityConfigException;
import org.wso2.carbon.security.config.SecurityServiceAdmin;
import org.wso2.carbon.user.api.ClaimMapping;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.utils.ServerConstants;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Application management service implementation. Which can use as an osgi
 * service and reuse this in admin service
 */
public class ApplicationManagementServiceImpl extends ApplicationManagementService {

    private static Log log = LogFactory.getLog(ApplicationManagementServiceImpl.class);
    private static volatile ApplicationManagementServiceImpl appMgtService;

    /**
     * Private constructor which not allow to create object from outside
     */
    private ApplicationManagementServiceImpl() {

    }

    /**
     * Get ApplicationManagementServiceImpl instance
     *
     * @return ApplicationManagementServiceImpl
     */
    public static ApplicationManagementServiceImpl getInstance() {
        if (appMgtService == null) {
            synchronized (ApplicationManagementServiceImpl.class) {
                if (appMgtService == null) {
                    appMgtService = new ApplicationManagementServiceImpl();
                }
            }
        }
        return appMgtService;
    }

    @Override
    public int createApplication(ServiceProvider serviceProvider, String tenantDomain, String userName)
            throws IdentityApplicationManagementException {
        try {

            startTenantFlow(tenantDomain, userName);

            // invoking the listeners
            List<ApplicationMgtListener> listeners = ApplicationMgtListenerServiceComponent.getListners();

            for (ApplicationMgtListener listener : listeners) {
                listener.createApplication(serviceProvider);
            }

            // first we need to create a role with the application name.
            // only the users in this role will be able to edit/update the
            // application.
            ApplicationMgtUtil.createAppRole(serviceProvider.getApplicationName());
            ApplicationDAO appDAO = ApplicationMgtSystemConfig.getInstance().getApplicationDAO();
            ApplicationMgtUtil.storePermission(serviceProvider.getApplicationName(),
                                               serviceProvider.getPermissionAndRoleConfig());

            return appDAO.createApplication(serviceProvider, tenantDomain);
        } catch (Exception e) {
            try {
                ApplicationMgtUtil.deleteAppRole(serviceProvider.getApplicationName());
                ApplicationMgtUtil.deletePermissions(serviceProvider.getApplicationName());
            } catch (Exception ignored) {
                if (log.isDebugEnabled()) {
                    log.debug("Ignored the exception occurred while trying to delete the role : ", e);
                }
            }
            String error = "Error occurred while creating the application : " + serviceProvider.getApplicationName();
            log.error(error, e);
            throw new IdentityApplicationManagementException(error, e);
        } finally {
            endTenantFlow();
        }
    }

    @Override
    public ServiceProvider getApplicationExcludingFileBasedSPs(String applicationName, String tenantDomain)
            throws IdentityApplicationManagementException {
        try {

            startTenantFlow(tenantDomain);

            ApplicationDAO appDAO = ApplicationMgtSystemConfig.getInstance().getApplicationDAO();
            ServiceProvider serviceProvider = appDAO.getApplication(applicationName, tenantDomain);
            loadApplicationPermissions(applicationName, serviceProvider);
            return serviceProvider;
        } catch (Exception e) {
            String error = "Error occurred while retrieving the application, " + applicationName;
            log.error(error, e);
            throw new IdentityApplicationManagementException(error, e);
        } finally {
            endTenantFlow();
        }
    }

    @Override
    public ApplicationBasicInfo[] getAllApplicationBasicInfo(String tenantDomain, String userName)
            throws IdentityApplicationManagementException {
        try {
            startTenantFlow(tenantDomain, userName);
            ApplicationDAO appDAO = ApplicationMgtSystemConfig.getInstance().getApplicationDAO();
            return appDAO.getAllApplicationBasicInfo();
        } catch (Exception e) {
            String error = "Error occurred while retrieving the all applications";
            log.error(error, e);
            throw new IdentityApplicationManagementException(error, e);
        } finally {
            endTenantFlow();
        }
    }

    @Override
    public void updateApplication(ServiceProvider serviceProvider, String tenantDomain, String userName)
            throws IdentityApplicationManagementException {
        try {

            try {
                startTenantFlow(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

                IdentityServiceProviderCacheKey cacheKey = new IdentityServiceProviderCacheKey(
                        tenantDomain, serviceProvider.getApplicationName());

                IdentityServiceProviderCache.getInstance().clearCacheEntry(cacheKey);

            } finally {
                endTenantFlow();
                startTenantFlow(tenantDomain, userName);
            }

            // invoking the listeners
            List<ApplicationMgtListener> listeners = ApplicationMgtListenerServiceComponent.getListners();
            for (ApplicationMgtListener listener : listeners) {
                listener.updateApplication(serviceProvider);
            }

            // check whether use is authorized to update the application.
            if (!ApplicationConstants.LOCAL_SP.equals(serviceProvider.getApplicationName()) &&
                !ApplicationMgtUtil.isUserAuthorized(serviceProvider.getApplicationName(),
                                                     serviceProvider.getApplicationID())) {
                log.warn("Illegal Access! User " +
                         CarbonContext.getThreadLocalCarbonContext().getUsername() +
                         " does not have access to the application " +
                         serviceProvider.getApplicationName());
                throw new IdentityApplicationManagementException("User not authorized");
            }

            ApplicationDAO appDAO = ApplicationMgtSystemConfig.getInstance().getApplicationDAO();
            String storedAppName = appDAO.getApplicationName(serviceProvider.getApplicationID());
            appDAO.updateApplication(serviceProvider);

            ApplicationPermission[] permissions = serviceProvider.getPermissionAndRoleConfig().getPermissions();
            String applicationNode = ApplicationMgtUtil.getApplicationPermissionPath() + RegistryConstants
                    .PATH_SEPARATOR + storedAppName;
            org.wso2.carbon.registry.api.Registry tenantGovReg = CarbonContext.getThreadLocalCarbonContext()
                    .getRegistry(RegistryType.USER_GOVERNANCE);

            boolean exist = tenantGovReg.resourceExists(applicationNode);
            if (exist && !storedAppName.equals(serviceProvider.getApplicationName())) {
                ApplicationMgtUtil.renameAppPermissionPathNode(storedAppName, serviceProvider.getApplicationName());
            }

            if (ArrayUtils.isNotEmpty(permissions)) {
                ApplicationMgtUtil.updatePermissions(serviceProvider.getApplicationName(), permissions);
            }
        } catch (Exception e) {
            String error = "Error occurred while updating the application";
            log.error(error, e);
            throw new IdentityApplicationManagementException(error, e);
        } finally {
            endTenantFlow();
        }
    }

    private void startTenantFlow(String tenantDomain)
            throws IdentityApplicationManagementException {
        int tenantId;
        try {
            tenantId = ApplicationManagementServiceComponentHolder.getInstance().getRealmService()
                    .getTenantManager().getTenantId(tenantDomain);
        } catch (UserStoreException e) {
            throw new IdentityApplicationManagementException("Error when setting tenant domain. ", e);
        }
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain);
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenantId);
    }

    private void startTenantFlow(String tenantDomain, String userName)
            throws IdentityApplicationManagementException {
        int tenantId;
        try {
            tenantId = ApplicationManagementServiceComponentHolder.getInstance().getRealmService()
                    .getTenantManager().getTenantId(tenantDomain);
        } catch (UserStoreException e) {
            throw new IdentityApplicationManagementException("Error when setting tenant domain. ", e);
        }
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain);
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenantId);
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(userName);
    }

    private void endTenantFlow() {
        PrivilegedCarbonContext.endTenantFlow();
    }

    @Override
    public void deleteApplication(String applicationName, String tenantDomain, String userName)
            throws IdentityApplicationManagementException {
        try {

            startTenantFlow(tenantDomain, userName);

            // invoking the listeners
            List<ApplicationMgtListener> listeners = ApplicationMgtListenerServiceComponent.getListners();

            for (ApplicationMgtListener listener : listeners) {
                listener.deleteApplication(applicationName);
            }

            if (!ApplicationMgtUtil.isUserAuthorized(applicationName)) {
                log.warn("Illegal Access! User " + CarbonContext.getThreadLocalCarbonContext().getUsername() +
                         " does not have access to the application " + applicationName);
                throw new IdentityApplicationManagementException("User not authorized");
            }

            ApplicationDAO appDAO = ApplicationMgtSystemConfig.getInstance().getApplicationDAO();
            ServiceProvider serviceProvider = appDAO.getApplication(applicationName, tenantDomain);
            appDAO.deleteApplication(applicationName);

            ApplicationMgtUtil.deleteAppRole(applicationName);
            ApplicationMgtUtil.deletePermissions(applicationName);

            if (serviceProvider != null &&
                serviceProvider.getInboundAuthenticationConfig() != null &&
                serviceProvider.getInboundAuthenticationConfig().getInboundAuthenticationRequestConfigs() != null) {

                InboundAuthenticationRequestConfig[] configs = serviceProvider.getInboundAuthenticationConfig()
                        .getInboundAuthenticationRequestConfigs();

                for (InboundAuthenticationRequestConfig config : configs) {

                    if (IdentityApplicationConstants.Authenticator.SAML2SSO.NAME.
                            equalsIgnoreCase(config.getInboundAuthType()) && config.getInboundAuthKey() != null) {

                        SAMLApplicationDAO samlDAO = ApplicationMgtSystemConfig.getInstance().getSAMLClientDAO();
                        samlDAO.removeServiceProviderConfiguration(config.getInboundAuthKey());

                    } else if (IdentityApplicationConstants.OAuth2.NAME.equalsIgnoreCase(config.getInboundAuthType()) &&
                               config.getInboundAuthKey() != null) {
                        OAuthApplicationDAO oathDAO = ApplicationMgtSystemConfig.getInstance().getOAuthOIDCClientDAO();
                        oathDAO.removeOAuthApplication(config.getInboundAuthKey());

                    } else if ("kerberos".equalsIgnoreCase(config.getInboundAuthType()) && config.getInboundAuthKey()
                            != null) {

//                        DirectoryServerManager directoryServerManager = new DirectoryServerManager();
//                        directoryServerManager.removeServer(config.getInboundAuthKey());

                    } else if(IdentityApplicationConstants.Authenticator.WSTrust.NAME.equalsIgnoreCase(
                                            config.getInboundAuthType()) && config.getInboundAuthKey() != null) {
                        try {
                            AxisService stsService = getAxisConfig().getService(ServerConstants.STS_NAME);
                            Parameter origParam =
                                    stsService.getParameter(SAMLTokenIssuerConfig.SAML_ISSUER_CONFIG.getLocalPart());

                            if (origParam != null) {
                                OMElement samlConfigElem = origParam.getParameterElement()
                                        .getFirstChildWithName(SAMLTokenIssuerConfig.SAML_ISSUER_CONFIG);

                                SAMLTokenIssuerConfig samlConfig = new SAMLTokenIssuerConfig(samlConfigElem);
                                samlConfig.getTrustedServices().remove(config.getInboundAuthKey());
                                setSTSParameter(samlConfig);
                                removeTrustedService(ServerConstants.STS_NAME, ServerConstants.STS_NAME,
                                        config.getInboundAuthKey());
                            } else {
                                throw new IdentityApplicationManagementException(
                                        "missing parameter : " + SAMLTokenIssuerConfig.SAML_ISSUER_CONFIG.getLocalPart());
                            }
                        } catch (Exception e) {
                            String error = "Error while removing a trusted service";
                            log.error(error, e);
                            throw new IdentityApplicationManagementException(error, e);
                        }
                    }
                }
            }

        } catch (Exception e) {
            String error = "Error occurred while deleting the application";
            log.error(error, e);
            throw new IdentityApplicationManagementException(error, e);
        } finally {
            endTenantFlow();
        }
    }

    @Override
    public IdentityProvider getIdentityProvider(String federatedIdPName, String tenantDomain)
            throws IdentityApplicationManagementException {
        try {
            startTenantFlow(tenantDomain);
            IdentityProviderDAO idpdao = ApplicationMgtSystemConfig.getInstance().getIdentityProviderDAO();
            return idpdao.getIdentityProvider(federatedIdPName);
        } catch (Exception e) {
            String error = "Error occurred while retrieving Identity Provider";
            log.error(error, e);
            throw new IdentityApplicationManagementException(error, e);
        } finally {
            endTenantFlow();
        }
    }

    @Override
    public IdentityProvider[] getAllIdentityProviders(String tenantDomain)
            throws IdentityApplicationManagementException {
        try {
            startTenantFlow(tenantDomain);
            IdentityProviderDAO idpdao = ApplicationMgtSystemConfig.getInstance().getIdentityProviderDAO();
            List<IdentityProvider> fedIdpList = idpdao.getAllIdentityProviders();
            if (fedIdpList != null) {
                return fedIdpList.toArray(new IdentityProvider[fedIdpList.size()]);
            }
            return new IdentityProvider[0];
        } catch (Exception e) {
            String error = "Error occurred while retrieving all Identity Providers";
            log.error(error, e);
            throw new IdentityApplicationManagementException(error, e);
        } finally {
            endTenantFlow();
        }
    }

    @Override
    public LocalAuthenticatorConfig[] getAllLocalAuthenticators(String tenantDomain)
            throws IdentityApplicationManagementException {
        try {
            startTenantFlow(tenantDomain);
            IdentityProviderDAO idpdao = ApplicationMgtSystemConfig.getInstance().getIdentityProviderDAO();
            List<LocalAuthenticatorConfig> localAuthenticators = idpdao.getAllLocalAuthenticators();
            if (localAuthenticators != null) {
                return localAuthenticators.toArray(new LocalAuthenticatorConfig[localAuthenticators.size()]);
            }
            return new LocalAuthenticatorConfig[0];
        } catch (Exception e) {
            String error = "Error occurred while retrieving all Local Authenticators";
            log.error(error, e);
            throw new IdentityApplicationManagementException(error, e);
        } finally {
            endTenantFlow();
        }
    }

    @Override
    public RequestPathAuthenticatorConfig[] getAllRequestPathAuthenticators(String tenantDomain)
            throws IdentityApplicationManagementException {
        try {
            startTenantFlow(tenantDomain);
            IdentityProviderDAO idpdao = ApplicationMgtSystemConfig.getInstance().getIdentityProviderDAO();
            List<RequestPathAuthenticatorConfig> reqPathAuthenticators = idpdao.getAllRequestPathAuthenticators();
            if (reqPathAuthenticators != null) {
                return reqPathAuthenticators.toArray(new RequestPathAuthenticatorConfig[reqPathAuthenticators.size()]);
            }
            return new RequestPathAuthenticatorConfig[0];
        } catch (Exception e) {
            String error = "Error occurred while retrieving all Request Path Authenticators";
            log.error(error, e);
            throw new IdentityApplicationManagementException(error, e);
        } finally {
            endTenantFlow();
        }
    }

    @Override
    public String[] getAllLocalClaimUris(String tenantDomain) throws IdentityApplicationManagementException {
        try {
            startTenantFlow(tenantDomain);
            String claimDialect = ApplicationMgtSystemConfig.getInstance().getClaimDialect();
            ClaimMapping[] claimMappings = CarbonContext.getThreadLocalCarbonContext().getUserRealm().getClaimManager()
                    .getAllClaimMappings(claimDialect);
            List<String> claimUris = new ArrayList<>();
            for (ClaimMapping claimMap : claimMappings) {
                claimUris.add(claimMap.getClaim().getClaimUri());
            }
            return claimUris.toArray(new String[claimUris.size()]);
        } catch (Exception e) {
            String error = "Error while reading system claims";
            log.error(error, e);
            throw new IdentityApplicationManagementException(error, e);
        } finally {
            endTenantFlow();
        }
    }

    @Override
    public String getServiceProviderNameByClientIdExcludingFileBasedSPs(String clientId, String type, String
            tenantDomain)
            throws IdentityApplicationManagementException {
        try {
            ApplicationDAO appDAO = ApplicationMgtSystemConfig.getInstance().getApplicationDAO();
            return appDAO.getServiceProviderNameByClientId(clientId, type, tenantDomain);

        } catch (Exception e) {
            String error = "Error occurred while retrieving the service provider for client id :  " + clientId;
            log.error(error, e);
            throw new IdentityApplicationManagementException(error, e);
        }
    }

    /**
     * [sp-claim-uri,local-idp-claim-uri]
     *
     * @param serviceProviderName
     * @param tenantDomain
     * @return
     * @throws IdentityApplicationManagementException
     */
    @Override
    public Map<String, String> getServiceProviderToLocalIdPClaimMapping(String serviceProviderName,
                                                                        String tenantDomain)
            throws IdentityApplicationManagementException {

        ApplicationDAO appDAO = ApplicationMgtSystemConfig.getInstance().getApplicationDAO();
        Map<String, String> claimMap = appDAO.getServiceProviderToLocalIdPClaimMapping(
                serviceProviderName, tenantDomain);

        if (claimMap == null
            || claimMap.isEmpty()
               && ApplicationManagementServiceComponent.getFileBasedSPs().containsKey(
                serviceProviderName)) {
            return new FileBasedApplicationDAO().getServiceProviderToLocalIdPClaimMapping(
                    serviceProviderName, tenantDomain);
        }

        return claimMap;
    }

    /**
     * [local-idp-claim-uri,sp-claim-uri]
     *
     * @param serviceProviderName
     * @param tenantDomain
     * @return
     * @throws IdentityApplicationManagementException
     */
    @Override
    public Map<String, String> getLocalIdPToServiceProviderClaimMapping(String serviceProviderName,
                                                                        String tenantDomain)
            throws IdentityApplicationManagementException {

        ApplicationDAO appDAO = ApplicationMgtSystemConfig.getInstance().getApplicationDAO();
        Map<String, String> claimMap = appDAO.getLocalIdPToServiceProviderClaimMapping(
                serviceProviderName, tenantDomain);

        if (claimMap == null
            || claimMap.isEmpty()
               && ApplicationManagementServiceComponent.getFileBasedSPs().containsKey(
                serviceProviderName)) {
            return new FileBasedApplicationDAO().getLocalIdPToServiceProviderClaimMapping(
                    serviceProviderName, tenantDomain);
        }
        return claimMap;

    }

    /**
     * Returns back the requested set of claims by the provided service provider in local idp claim
     * dialect.
     *
     * @param serviceProviderName
     * @param tenantDomain
     * @return
     * @throws IdentityApplicationManagementException
     */
    @Override
    public List<String> getAllRequestedClaimsByServiceProvider(String serviceProviderName,
                                                               String tenantDomain)
            throws IdentityApplicationManagementException {

        ApplicationDAO appDAO = ApplicationMgtSystemConfig.getInstance().getApplicationDAO();
        List<String> reqClaims = appDAO.getAllRequestedClaimsByServiceProvider(serviceProviderName,
                                                                               tenantDomain);

        if (reqClaims == null
            || reqClaims.isEmpty()
               && ApplicationManagementServiceComponent.getFileBasedSPs().containsKey(
                serviceProviderName)) {
            return new FileBasedApplicationDAO().getAllRequestedClaimsByServiceProvider(
                    serviceProviderName, tenantDomain);
        }

        return reqClaims;
    }

    /**
     * @param clientId
     * @param clientType
     * @param tenantDomain
     * @return
     * @throws IdentityApplicationManagementException
     */
    @Override
    public String getServiceProviderNameByClientId(String clientId, String clientType,
                                                   String tenantDomain) throws IdentityApplicationManagementException {

        String name;

        ApplicationDAO appDAO = ApplicationMgtSystemConfig.getInstance().getApplicationDAO();
        name = appDAO.getServiceProviderNameByClientId(clientId, clientType, tenantDomain);

        if (name == null) {
            name = new FileBasedApplicationDAO().getServiceProviderNameByClientId(clientId,
                                                                                  clientType, tenantDomain);
        }

        if (name == null) {
            ServiceProvider defaultSP = ApplicationManagementServiceComponent.getFileBasedSPs()
                    .get(IdentityApplicationConstants.DEFAULT_SP_CONFIG);
            name = defaultSP.getApplicationName();
        }

        return name;

    }

    /**
     * @param serviceProviderName
     * @param tenantDomain
     * @return
     * @throws IdentityApplicationManagementException
     */
    @Override
    public ServiceProvider getServiceProvider(String serviceProviderName, String tenantDomain)
            throws IdentityApplicationManagementException {

        startTenantFlow(tenantDomain);
        ApplicationDAO appDAO = ApplicationMgtSystemConfig.getInstance().getApplicationDAO();
        ServiceProvider serviceProvider = appDAO.getApplication(serviceProviderName, tenantDomain);

        if (serviceProvider != null) {
            loadApplicationPermissions(serviceProviderName, serviceProvider);
        }

        if (serviceProvider == null
            && ApplicationManagementServiceComponent.getFileBasedSPs().containsKey(
                serviceProviderName)) {
            serviceProvider = ApplicationManagementServiceComponent.getFileBasedSPs().get(
                    serviceProviderName);
        }
        endTenantFlow();
        return serviceProvider;
    }

    /**
     * @param clientId
     * @param clientType
     * @param tenantDomain
     * @return
     * @throws IdentityApplicationManagementException
     */
    @Override
    public ServiceProvider getServiceProviderByClientId(String clientId, String clientType, String tenantDomain)
            throws IdentityApplicationManagementException {

        // client id can contain the @ to identify the tenant domain.
        if (clientId != null && clientId.contains("@")) {
            clientId = clientId.split("@")[0];
        }

        String serviceProviderName;
        ServiceProvider serviceProvider = null;

        serviceProviderName = getServiceProviderNameByClientId(clientId, clientType, tenantDomain);

        try {
            startTenantFlow(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

            IdentityServiceProviderCacheKey cacheKey = new IdentityServiceProviderCacheKey(
                    tenantDomain, serviceProviderName);
            IdentityServiceProviderCacheEntry entry = ((IdentityServiceProviderCacheEntry) IdentityServiceProviderCache
                    .getInstance().getValueFromCache(cacheKey));

            if (entry != null) {
                return entry.getServiceProvider();
            }

        } finally {
            endTenantFlow();
            startTenantFlow(tenantDomain);
        }

        if (serviceProviderName != null) {
            ApplicationDAO appDAO = ApplicationMgtSystemConfig.getInstance().getApplicationDAO();
            serviceProvider = appDAO.getApplication(serviceProviderName, tenantDomain);

            if (serviceProvider != null) {
                // if "Authentication Type" is "Default" we must get the steps from the default SP
                AuthenticationStep[] authenticationSteps = serviceProvider
                        .getLocalAndOutBoundAuthenticationConfig().getAuthenticationSteps();

                loadApplicationPermissions(serviceProviderName, serviceProvider);

                if (authenticationSteps == null || authenticationSteps.length == 0) {
                    ServiceProvider defaultSP = ApplicationManagementServiceComponent
                            .getFileBasedSPs().get(IdentityApplicationConstants.DEFAULT_SP_CONFIG);
                    authenticationSteps = defaultSP.getLocalAndOutBoundAuthenticationConfig()
                            .getAuthenticationSteps();
                    serviceProvider.getLocalAndOutBoundAuthenticationConfig()
                            .setAuthenticationSteps(authenticationSteps);
                }
            }
        }

        if (serviceProvider == null
            && serviceProviderName != null
            && ApplicationManagementServiceComponent.getFileBasedSPs().containsKey(
                serviceProviderName)) {
            serviceProvider = ApplicationManagementServiceComponent.getFileBasedSPs().get(
                    serviceProviderName);
        }

        endTenantFlow();

        try {
            startTenantFlow(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

            IdentityServiceProviderCacheKey cacheKey = new IdentityServiceProviderCacheKey(
                    tenantDomain, serviceProviderName);
            IdentityServiceProviderCacheEntry entry = new IdentityServiceProviderCacheEntry();
            entry.setServiceProvider(serviceProvider);
            IdentityServiceProviderCache.getInstance().addToCache(cacheKey, entry);
        } finally {
            endTenantFlow();
        }
        return serviceProvider;
    }

    private void loadApplicationPermissions(String serviceProviderName, ServiceProvider serviceProvider)
            throws IdentityApplicationManagementException {
        List<ApplicationPermission> permissionList = ApplicationMgtUtil.loadPermissions(serviceProviderName);

        if (permissionList != null) {
            PermissionsAndRoleConfig permissionAndRoleConfig;
            if (serviceProvider.getPermissionAndRoleConfig() == null) {
                permissionAndRoleConfig = new PermissionsAndRoleConfig();
            } else {
                permissionAndRoleConfig = serviceProvider.getPermissionAndRoleConfig();
            }
            permissionAndRoleConfig.setPermissions(permissionList.toArray(
                    new ApplicationPermission[permissionList.size()]));
            serviceProvider.setPermissionAndRoleConfig(permissionAndRoleConfig);
        }
    }

    /**
     * Set STS parameters
     *
     * @param samlConfig SAML config
     * @throws org.apache.axis2.AxisFault
     * @throws org.wso2.carbon.registry.api.RegistryException
     */
    private void setSTSParameter(SAMLTokenIssuerConfig samlConfig) throws AxisFault,
                                                                          RegistryException {
        new SecurityServiceAdmin(getAxisConfig(), getConfigSystemRegistry()).
                setServiceParameterElement(ServerConstants.STS_NAME, samlConfig.getParameter());
    }

    /**
     * Remove trusted service
     *
     * @param groupName      Group name
     * @param serviceName    Service name
     * @param trustedService Trusted service name
     * @throws org.wso2.carbon.security.SecurityConfigException
     */
    private void removeTrustedService(String groupName, String serviceName, String trustedService)
            throws SecurityConfigException {
        Registry registry;
        String resourcePath;
        Resource resource;
        try {
            resourcePath = RegistryResources.SERVICE_GROUPS + groupName +
                    RegistryResources.SERVICES + serviceName + "/trustedServices";
            registry = getConfigSystemRegistry();
            if (registry != null) {
                if (registry.resourceExists(resourcePath)) {
                    resource = registry.get(resourcePath);
                    if (resource.getProperty(trustedService) != null) {
                        resource.removeProperty(trustedService);
                    }
                    registry.put(resourcePath, resource);
                }
            }
        } catch (Exception e) {
            String error = "Error occurred while removing trusted service for STS";
            log.error(error, e);
            throw new SecurityConfigException(error, e);
        }
    }

    /**
     * Get axis config
     *
     * @return axis configuration
     */
    private AxisConfiguration getAxisConfig() {
        return ApplicationManagementServiceComponentHolder.getInstance().getConfigContextService()
                .getServerConfigContext().getAxisConfiguration();
    }

    /**
     * Get config system registry
     *
     * @return config system registry
     * @throws org.wso2.carbon.registry.api.RegistryException
     */
    private Registry getConfigSystemRegistry() throws RegistryException {
        return (Registry) ApplicationManagementServiceComponentHolder.getInstance().getRegistryService()
                .getConfigSystemRegistry();
    }

}
