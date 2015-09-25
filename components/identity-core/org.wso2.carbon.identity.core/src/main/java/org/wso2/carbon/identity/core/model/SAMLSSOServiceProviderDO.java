/*
 * Copyright 2005-2007 WSO2, Inc. (http://wso2.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.identity.core.model;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SAMLSSOServiceProviderDO implements Serializable {

    private static final long serialVersionUID = -1213957008659821807L;
    String tenantDomain;
    private String issuer;
    private String assertionConsumerUrl;
    private String[] assertionConsumerUrls;
    private List<String> assertionConsumerUrlList;
    private String defaultAssertionConsumerUrl;
    private String certAlias;
    private String sloResponseURL;
    private String sloRequestURL;
    private boolean doSingleLogout;
    private String loginPageURL;
    private boolean doSignResponse;
    private boolean doSignAssertions;
    private String attributeConsumingServiceIndex;
    private String[] requestedClaims;
    private List<String> requestedClaimsList;
    private String[] requestedAudiences;
    private List<String> requestedAudiencesList;
    private String[] requestedRecipients;
    private List<String> requestedRecipientsList;
    private boolean enableAttributesByDefault;
    private String nameIdClaimUri;
    private String nameIDFormat;
    private boolean isIdPInitSSOEnabled;
    private boolean idPInitSLOEnabled;
    private String[] idpInitSLOReturnToURLs;
    private List<String> idpInitSLOReturnToURLList;
    private boolean doEnableEncryptedAssertion;
    private boolean doValidateSignatureInRequests;
    private String signingAlgorithm;
    private String digestAlgorithm;

    public String getNameIDFormat() {
        return nameIDFormat;
    }

    public void setNameIDFormat(String nameIDFormat) {
        this.nameIDFormat = nameIDFormat;
    }

    public String getNameIdClaimUri() {
        return nameIdClaimUri;
    }

    public void setNameIdClaimUri(String nameIdClaimUri) {
        this.nameIdClaimUri = nameIdClaimUri;
    }

    public boolean isEnableAttributesByDefault() {
        return enableAttributesByDefault;
    }

    public void setEnableAttributesByDefault(boolean enableAttributesByDefault) {
        this.enableAttributesByDefault = enableAttributesByDefault;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        if (issuer != null) {
            this.issuer = issuer.replaceAll("[\n\r]", "").trim();
        }
    }

    public String getAssertionConsumerUrl() {
        return assertionConsumerUrl;
    }

    public void setAssertionConsumerUrl(String assertionConsumerUrl) {
        if (assertionConsumerUrl != null) {
            this.assertionConsumerUrl = assertionConsumerUrl.replaceAll("[\n\r]", "").trim();
        }
    }

    public String getCertAlias() {
        return certAlias;
    }

    public void setCertAlias(String certAlias) {
        this.certAlias = certAlias;
    }

    public String getSloResponseURL() {
        return sloResponseURL;
    }

    public void setSloResponseURL(String sloResponseURL) {
        if (sloResponseURL != null) {
            this.sloResponseURL = sloResponseURL.replaceAll("[\n\r]", "").trim();
        }
    }

    public boolean isDoSingleLogout() {
        return doSingleLogout;
    }

    public void setDoSingleLogout(boolean doSingleLogout) {
        this.doSingleLogout = doSingleLogout;
    }

    public String getLoginPageURL() {
        return loginPageURL;
    }

    public void setLoginPageURL(String loginPageURL) {
        if(StringUtils.isNotBlank(loginPageURL)) {
            this.loginPageURL = loginPageURL.replaceAll("[\n\r]", "").trim();
        } else {
            this.loginPageURL = null;
        }
    }

    public boolean isDoSignAssertions() {
        return doSignAssertions;
    }

    public void setDoSignAssertions(boolean doSignAssertions) {
        this.doSignAssertions = doSignAssertions;
    }

    public String getAttributeConsumingServiceIndex() {
        return attributeConsumingServiceIndex;
    }

    public void setAttributeConsumingServiceIndex(String attributeConsumingServiceIndex) {
        this.attributeConsumingServiceIndex = attributeConsumingServiceIndex;
    }

    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    public void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    public String getSigningAlgorithm() {
        return signingAlgorithm;
    }

    public void setSigningAlgorithm(String signingAlgorithm) {
        if (StringUtils.isNotEmpty(signingAlgorithm)) {
            this.signingAlgorithm = signingAlgorithm;
        }
    }

    /**
     * @return the requestedClaims
     */
    public String[] getRequestedClaims() {
        if (requestedClaims != null) {
            return requestedClaims.clone();
        } else {
            return ArrayUtils.EMPTY_STRING_ARRAY;
        }
    }

    /**
     * @param requestedClaims the requestedClaims to set
     */
    public void setRequestedClaims(List<String> requestedClaims) {
        if (requestedClaims != null) {
            this.requestedClaimsList = requestedClaims;
            this.requestedClaims = requestedClaims.toArray(new String[requestedClaims.size()]);
        }
    }

    /**
     * @param requestedClaims the requestedClaims to set
     */
    public void setRequestedClaims(String[] requestedClaims) {
        if (requestedClaims != null) {
            this.requestedClaims = requestedClaims.clone();
            this.requestedClaimsList = Arrays.asList(requestedClaims);
        }
    }

    /**
     * @return the requestedClaims
     */
    public List<String> getRequestedClaimsList() {
        if (requestedClaimsList != null) {
            return requestedClaimsList;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * @return the requestedAudiences
     */
    public String[] getRequestedAudiences() {
        if (requestedAudiences != null) {
            return requestedAudiences.clone();
        } else {
            return ArrayUtils.EMPTY_STRING_ARRAY;
        }
    }

    /**
     * @param requestedAudiences the requestedAudiences to set
     */
    public void setRequestedAudiences(List<String> requestedAudiences) {
        if (requestedAudiences != null) {
            this.requestedAudiencesList = requestedAudiences;
            this.requestedAudiences = requestedAudiences.toArray(new String[requestedAudiencesList.size()]);
        }
    }

    /**
     * @param requestedAudiences the requestedAudiences to set
     */
    public void setRequestedAudiences(String[] requestedAudiences) {
        if (requestedAudiences != null) {
            this.requestedAudiences = requestedAudiences.clone();
            this.requestedAudiencesList = Arrays.asList(requestedAudiences);
        }
    }

    /**
     * @return the requestedAudiences
     */
    public List<String> getRequestedAudiencesList() {
        if (requestedAudiencesList != null) {
            return requestedAudiencesList;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * @return the requestedRecipients
     */
    public String[] getRequestedRecipients() {
        if (requestedRecipients != null) {
            return requestedRecipients.clone();
        } else {
            return ArrayUtils.EMPTY_STRING_ARRAY;
        }
    }

    /**
     * @param requestedRecipientsList the requestedRecipients to set
     */
    public void setRequestedRecipients(List<String> requestedRecipientsList) {
        this.requestedRecipientsList = requestedRecipientsList;
        if (requestedRecipientsList != null) {
            this.requestedRecipients = requestedRecipientsList.toArray(new String[requestedRecipientsList.size()]);
        } else {
            this.requestedRecipients = null;
        }
    }

    /**
     * @param requestedRecipients the requestedRecipients to set
     */
    public void setRequestedRecipients(String[] requestedRecipients) {
        if (requestedRecipients != null) {
            this.requestedRecipients = requestedRecipients.clone();
            this.requestedRecipientsList = Arrays.asList(requestedRecipients);
        } else {
            this.requestedRecipients = null;
            this.requestedRecipientsList = null;
        }
    }

    /**
     * @return the requestedRecipients
     */
    public List<String> getRequestedRecipientsList() {
        if (requestedRecipientsList != null) {
            return requestedRecipientsList;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * @return the doSignResponse
     */
    public boolean isDoSignResponse() {
        return doSignResponse;
    }

    /**
     * @param doSignResponse the doSignResponse to set
     */
    public void setDoSignResponse(boolean doSignResponse) {
        this.doSignResponse = doSignResponse;
    }

    public boolean isIdPInitSSOEnabled() {
        return isIdPInitSSOEnabled;
    }

    public void setIdPInitSSOEnabled(boolean idPInitSSOEnabled) {
        isIdPInitSSOEnabled = idPInitSSOEnabled;
    }

    public boolean isDoEnableEncryptedAssertion() {
        return doEnableEncryptedAssertion;
    }

    public void setDoEnableEncryptedAssertion(boolean doEnableEncryptedAssertion) {
        this.doEnableEncryptedAssertion = doEnableEncryptedAssertion;
    }

    public boolean isDoValidateSignatureInRequests() {
        return doValidateSignatureInRequests;
    }

    public void setDoValidateSignatureInRequests(boolean doValidateSignatureInRequests) {
        this.doValidateSignatureInRequests = doValidateSignatureInRequests;
    }

    public String getTenantDomain() {
        return tenantDomain;
    }

    public void setTenantDomain(String tenantDomain) {
        this.tenantDomain = tenantDomain;
    }

    public String[] getAssertionConsumerUrls() {
        if (assertionConsumerUrls != null) {
            return assertionConsumerUrls.clone();
        } else {
            return ArrayUtils.EMPTY_STRING_ARRAY;
        }
    }

    public List<String> getAssertionConsumerUrlList() {
        if (assertionConsumerUrlList != null) {
            return assertionConsumerUrlList;
        } else {
            return Collections.emptyList();
        }
    }

    public void setAssertionConsumerUrls(String[] assertionConsumerUrls) {
        if (assertionConsumerUrls != null) {
            this.assertionConsumerUrls = assertionConsumerUrls.clone();
            this.assertionConsumerUrlList = Arrays.asList(assertionConsumerUrls);
        } else {
            this.assertionConsumerUrls = null;
            this.assertionConsumerUrlList = null;
        }
    }

    public void setAssertionConsumerUrls(List<String> assertionConsumerUrlList) {
        this.assertionConsumerUrlList = assertionConsumerUrlList;
        if (assertionConsumerUrlList != null) {
            this.assertionConsumerUrls = assertionConsumerUrlList.toArray(new String[assertionConsumerUrlList.size()]);
        } else {
            this.assertionConsumerUrls = null;
        }
    }

    public String getDefaultAssertionConsumerUrl() {
        return defaultAssertionConsumerUrl;
    }

    public void setDefaultAssertionConsumerUrl(String defaultAssertionConsumerUrl) {
        if(StringUtils.isNotBlank(defaultAssertionConsumerUrl)) {
            this.defaultAssertionConsumerUrl = defaultAssertionConsumerUrl.replaceAll("[\n\r]", "").trim();
        } else {
            this.defaultAssertionConsumerUrl = null;
        }
    }

    public String getSloRequestURL() {
        return sloRequestURL;
    }

    public void setSloRequestURL(String sloRequestURL) {
        if(StringUtils.isNotBlank(sloRequestURL)) {
            this.sloRequestURL = sloRequestURL.replaceAll("[\n\r]", "").trim();
        } else {
            this.sloRequestURL = null;
        }
    }

    public boolean isIdPInitSLOEnabled() {
        return idPInitSLOEnabled;
    }

    public void setIdPInitSLOEnabled(boolean idPInitSLOEnabled) {
        this.idPInitSLOEnabled = idPInitSLOEnabled;
    }

    public String[] getIdpInitSLOReturnToURLs() {
        if (idpInitSLOReturnToURLs != null) {
            return idpInitSLOReturnToURLs.clone();
        } else {
            return ArrayUtils.EMPTY_STRING_ARRAY;
        }
    }

    public void setIdpInitSLOReturnToURLs(String[] idpInitSLOReturnToURLs) {
        if (idpInitSLOReturnToURLs != null) {
            this.idpInitSLOReturnToURLs = idpInitSLOReturnToURLs.clone();
            this.idpInitSLOReturnToURLList = Arrays.asList(idpInitSLOReturnToURLs);
        } else {
            this.idpInitSLOReturnToURLs = null;
            this.idpInitSLOReturnToURLList = null;
        }
    }

    public List<String> getIdpInitSLOReturnToURLList() {
        if (idpInitSLOReturnToURLList != null) {
            return idpInitSLOReturnToURLList;
        } else {
            return Collections.emptyList();
        }
    }

    public void setIdpInitSLOReturnToURLs(List<String> idpInitSLOReturnToURLList) {
        this.idpInitSLOReturnToURLList = idpInitSLOReturnToURLList;
        if (idpInitSLOReturnToURLList != null) {
            this.idpInitSLOReturnToURLs = idpInitSLOReturnToURLList.toArray(new String[idpInitSLOReturnToURLList.size()]);
        } else {
            this.idpInitSLOReturnToURLs = null;
        }
    }
}
