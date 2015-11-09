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
package org.wso2.carbon.identity.application.authentication.framework.inbound;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.wso2.carbon.identity.application.authentication.framework.cache.AuthenticationRequestCacheEntry;
import org.wso2.carbon.identity.application.authentication.framework.exception.FrameworkException;
import org.wso2.carbon.identity.application.authentication.framework.internal.FrameworkServiceDataHolder;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticationRequest;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkConstants;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.application.common.IdentityApplicationManagementException;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.registry.core.utils.UUIDGenerator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CommonInboundAuthenticationServlet extends HttpServlet {

    private InboundAuthenticationManager inboundAuthenticationManager = new InboundAuthenticationManager();

    private InboundAuthenticationRequestBuilder getInboundRequestBuilder(HttpServletRequest req,
            HttpServletResponse resp) throws FrameworkException {
        List<InboundAuthenticationRequestBuilder> requestBuilders = FrameworkServiceDataHolder.getInstance()
                .getInboundAuthenticationRequestBuilders();

        for (InboundAuthenticationRequestBuilder requestBuilder : requestBuilders) {
            if (requestBuilder.canHandle(req, resp)) {
                return requestBuilder;
            }
        }
        return null;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {

            doProcess(request, response);

        } catch (IOException | ServletException | FrameworkException | IdentityApplicationManagementException ex) {
            throw new ServletException(ex);
        }
    }

    private void doProcess(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, IdentityApplicationManagementException, FrameworkException {

        InboundAuthenticationRequestBuilder requestBuilder = getInboundRequestBuilder(request, response);
        InboundAuthenticationRequest authenticationRequest = requestBuilder.buildRequest(request, response);

        if (request.getPathInfo().contains(InboundAuthenticationConstants.HTTP_PATH_PARAM_REQUEST)) {
            doProcessRequest(request, response, authenticationRequest);
        } else if (request.getPathInfo().contains(InboundAuthenticationConstants.HTTP_PATH_PARAM_RESPONSE)) {
            InboundAuthenticationResponse result = doProcessResponse(authenticationRequest);
            if (result.getRedirectURL() != null) {
                response.sendRedirect(result.getRedirectURL());
            }
        }
    }

    protected InboundAuthenticationResponse doProcessRequest(HttpServletRequest request, HttpServletResponse response,
            InboundAuthenticationRequest authenticationRequest)
            throws ServletException, IOException, IdentityApplicationManagementException, FrameworkException {

        InboundAuthenticationResponse result = inboundAuthenticationManager.processRequest(authenticationRequest);
        InboundAuthenticationContext context = new InboundAuthenticationContext();
        context.setAuthenticationRequest(authenticationRequest);
        sendToFrameworkForAuthentication(request, response, context, result);
        return result;
    }

    protected InboundAuthenticationResponse doProcessResponse(InboundAuthenticationRequest authenticationRequest)
            throws ServletException, IOException, IdentityApplicationManagementException, FrameworkException {

        String []sessionDataKey = authenticationRequest.getParameters().get(FrameworkConstants.SESSION_DATA_KEY);
        if(!ArrayUtils.isEmpty(sessionDataKey) && !StringUtils.isEmpty(sessionDataKey[0])){
            InboundAuthenticationContextCacheEntry cacheEntry = InboundAuthenticationUtil
                    .getInboundAuthenticationContextToCache(sessionDataKey[0]);

            InboundAuthenticationResponse result = inboundAuthenticationManager.processResponse(
                    cacheEntry.getInboundAuthenticationContext(), authenticationRequest);
            return result;
        }
        throw new FrameworkException("No session found to process the response.");
    }

    protected void sendToFrameworkForAuthentication(HttpServletRequest req, HttpServletResponse resp,
            InboundAuthenticationContext context, InboundAuthenticationResponse inboundAuthenticationResponse)
            throws ServletException, IOException, IdentityApplicationManagementException, FrameworkException {

        String sessionDataKey = UUIDGenerator.generateUUID();
        String authName = inboundAuthenticationResponse.getInboundAuthenticationRequestProcessor().getName();
        String relyingParty = inboundAuthenticationResponse.getInboundAuthenticationRequestProcessor().getRelyingPartyId();
        String callbackPath = inboundAuthenticationResponse.getInboundAuthenticationRequestProcessor().getCallbackPath(context);


        AuthenticationRequest authenticationRequest = new AuthenticationRequest();
        InboundAuthenticationRequest inboundAuthenticationRequest = context.getAuthenticationRequest();

        Map<String, String[]> parameterMap = inboundAuthenticationRequest.getParameters();

        parameterMap.put(FrameworkConstants.SESSION_DATA_KEY, new String[] { sessionDataKey });
        parameterMap.put("type", new String[] { authName });

        authenticationRequest.appendRequestQueryParams(parameterMap);

        for (Map.Entry<String, String> entry : inboundAuthenticationRequest.getHeaders().entrySet()) {
            authenticationRequest.addHeader(entry.getKey(), entry.getValue());
        }

        authenticationRequest.setRelyingParty(relyingParty);
        authenticationRequest.setType(authName);
        authenticationRequest.setCommonAuthCallerPath(URLEncoder.encode(callbackPath, "UTF-8"));

        AuthenticationRequestCacheEntry authRequest = new AuthenticationRequestCacheEntry(authenticationRequest);
        FrameworkUtils.addAuthenticationRequestToCache(sessionDataKey, authRequest);

        InboundAuthenticationContextCacheEntry contextCacheEntry = new InboundAuthenticationContextCacheEntry(context);
        InboundAuthenticationUtil.addInboundAuthenticationContextToCache(sessionDataKey, contextCacheEntry);

        String queryParams = "?sessionDataKey=" + sessionDataKey + "&" + "type" + "=" + authName;

        String commonAuthURL = IdentityUtil.getServerURL(FrameworkConstants.COMMONAUTH, true);

        if (inboundAuthenticationResponse.getInboundAuthenticationRequestProcessor().isDirectResponseRequired()) {
            FrameworkUtils.getRequestCoordinator().handle(req, resp);
        } else {
            resp.sendRedirect(commonAuthURL + queryParams);
        }
    }
}
