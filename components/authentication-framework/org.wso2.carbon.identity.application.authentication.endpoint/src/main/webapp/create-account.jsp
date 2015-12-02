<!--
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
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
-->

<%@ page import="org.owasp.encoder.Encode" %>
<%@ page
        import="org.wso2.carbon.identity.application.authentication.endpoint.util.UserRegistrationAdminServiceClient" %>
<%@ page import="org.wso2.carbon.identity.user.registration.stub.dto.UserFieldDTO" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>

<%
    String errorCode = request.getParameter("errorCode");
    String failedPrevious = request.getParameter("failedPrevious");

    UserRegistrationAdminServiceClient registrationClient = new UserRegistrationAdminServiceClient();
    UserFieldDTO[] userFields = new UserFieldDTO[0];
    String forwardTo = null;
    List<UserFieldDTO> fields = new ArrayList<UserFieldDTO>();

    boolean isFirstNameInClaims = false;
    boolean isFirstNameRequired = false;
    boolean isLastNameInClaims = false;
    boolean isLastNameRequired = false;
    boolean isEmailInClaims = false;
    boolean isEmailRequired = false;

    try {
        userFields = registrationClient.readUserFieldsForUserRegistration("http://wso2.org/claims");
        for(UserFieldDTO userFieldDTO : userFields) {
            if (StringUtils.equals(userFieldDTO.getFieldName(), "First Name")) {
                isFirstNameInClaims = true;
                isFirstNameRequired = userFieldDTO.getRequired();
            }
            if (StringUtils.equals(userFieldDTO.getFieldName(), "Last Name")) {
                isLastNameInClaims = true;
                isLastNameRequired = userFieldDTO.getRequired();
            }
            if (StringUtils.equals(userFieldDTO.getFieldName(), "Email")) {
                isEmailInClaims = true;
                isEmailRequired = userFieldDTO.getRequired();
            }
        }
    } catch (Exception e) {
        failedPrevious = "true";
        errorCode = e.getMessage();
    }
%>
<fmt:bundle basename="org.wso2.carbon.identity.application.authentication.endpoint.i18n.Resources">
    <html>
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>WSO2 Identity Server</title>

        <link rel="icon" href="images/favicon.png" type="image/x-icon"/>
        <link href="libs/bootstrap_3.3.5/css/bootstrap.min.css" rel="stylesheet">
        <link href="css/Roboto.css" rel="stylesheet">
        <link href="css/custom-common.css" rel="stylesheet">

        <!--[if lt IE 9]>
        <script src="js/html5shiv.min.js"></script>
        <script src="js/respond.min.js"></script>
        <![endif]-->
    </head>

    <body>

    <!-- header -->
    <header class="header header-default">
        <div class="container-fluid"><br></div>
        <div class="container-fluid">
            <div class="pull-left brand float-remove-xs text-center-xs">
                <a href="#">
                    <img src="images/logo-inverse.svg" alt="wso2" title="wso2" class="logo">

                    <h1><em>Identity Server</em></h1>
                </a>
            </div>
        </div>
    </header>

    <!-- page content -->
    <div class="container-fluid body-wrapper">

        <div class="row">
            <!-- content -->
            <div class="col-xs-12 col-sm-10 col-md-8 col-lg-5 col-centered wr-login">
                <form action="registration.do" method="post" id="register">
                    <h2 class="wr-title uppercase blue-bg padding-double white boarder-bottom-blue margin-none">Create
                        An Account</h2>

                    <div class="clearfix"></div>
                    <div class="boarder-all ">

                        <% if (failedPrevious != null && failedPrevious.equals("true")) { %>
                            <div class="alert alert-danger" id="server-error-msg">
                                <%= Encode.forHtmlContent(errorCode) %>
                            </div>
                        <% } %>

                        <div class="alert alert-danger" id="error-msg" hidden="hidden">
                        </div>

                        <div class="padding-double font-large">Enter required fields to complete registration</div>
                        <!-- validation -->
                        <div class="padding-double">
                            <div id="regFormError" class="alert alert-danger" style="display:none"></div>
                            <div id="regFormSuc" class="alert alert-success" style="display:none"></div>

                            <% if(isFirstNameInClaims) { %>
                            <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6 form-group">
                                <label>First Name</label>
                                <input type="text" name="First Name"
                                       data-claim-uri="http://wso2.org/claims/givenname"
                                       class="form-control"
                                       <% if (isFirstNameRequired) {%> required <%}%>>
                            </div>
                            <%}%>

                            <% if(isLastNameInClaims) { %>
                            <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6 form-group">
                                <label>Last Name</label>
                                <input type="text" name="Last Name" data-claim-uri="http://wso2.org/claims/lastname"
                                       class="form-control  required null"
                                       <% if (isLastNameRequired) {%> required <%}%>>
                            </div>
                            <%}%>

                            <div class="col-xs-12 col-sm-12 col-md-12 col-lg-12 form-group">
                                <label>Username</label>
                                <input id="reg-username" name="reg_username" type="text"
                                       class="form-control required usrName usrNameLength" required>
                            </div>

                            <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6 form-group">
                                <label>Password</label>
                                <input id="reg-password" name="reg_password" type="password"
                                       class="form-control" required>
                            </div>

                            <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6 form-group">
                                <label>Confirm password</label>
                                <input id="reg-password2" name="reg-password2" type="password" class="form-control"
                                       data-match="reg-password" required>
                            </div>

                            <% if(isEmailInClaims) { %>
                            <div class="col-xs-12 col-sm-12 col-md-12 col-lg-12 form-group">
                                <label>Email</label>
                                <input type="email" name="Email" data-claim-uri="http://wso2.org/claims/emailaddress"
                                       class="form-control" data-validate="email"
                                       <% if (isEmailRequired) {%> required <%}%>>
                            </div>
                            <%}%>

                            <% for (UserFieldDTO userFieldDTO : userFields) {
                                if (userFieldDTO.getSupportedByDefault() &&
                                        !StringUtils.equals(userFieldDTO.getFieldName(), "Username") &&
                                        !StringUtils.equals(userFieldDTO.getFieldName(), "Last Name") &&
                                        !StringUtils.equals(userFieldDTO.getFieldName(), "First Name") &&
                                        !StringUtils.equals(userFieldDTO.getFieldName(), "Email")) {
                            %>
                                        <div class="col-xs-12 col-sm-12 col-md-12 col-lg-12 form-group">
                                            <label><%= Encode.forHtmlContent(userFieldDTO.getFieldName()) %></label>
                                            <input name="<%= Encode.forHtmlAttribute(userFieldDTO.getFieldName()) %>"
                                             data-claim-uri="<%= Encode.forHtmlAttribute(userFieldDTO.getClaimUri()) %>"
                                             class="form-control"
                                             <% if (userFieldDTO.getRequired()) {%> required <%}%>>
                                        </div>
                            <%          fields.add(userFieldDTO);
                                    }
                                }
                                session.setAttribute("fields", fields);
                            %>

                            <div class="col-xs-12 col-sm-12 col-md-12 col-lg-12 form-group">
                                <input type="hidden" name="sessionDataKey" value='<%=Encode.forHtmlAttribute
            (request.getParameter("sessionDataKey"))%>'/>
                            </div>

                            <div class="col-xs-12 col-sm-12 col-md-12 col-lg-12 form-group">
                                <br><br>
                                <button id="registrationSubmit"
                                        class="wr-btn grey-bg col-xs-12 col-md-12 col-lg-12 uppercase font-extra-large"
                                        type="submit">Register
                                </button>
                            </div>
                            <div class="col-xs-12 col-sm-12 col-md-12 col-lg-12 form-group">
                                <span class="margin-top padding-top-double font-large">Already have an account? </span>
                                <a href="../dashboard/index.jag" id="signInLink" class="font-large">Sign in</a>
                            </div>
                            <div class="clearfix"></div>
                        </div>
                    </div>
                </form>
            </div>
        </div>
        <!-- /content/body -->

    </div>

    <!-- footer -->
    <footer class="footer" style="position: relative">
        <div class="container-fluid">
            <p>WSO2 Identity Server | &copy;
                <script>document.write(new Date().getFullYear());</script>
                <a href="http://wso2.com/" target="_blank"><i class="icon fw fw-wso2"></i> Inc</a>. All Rights Reserved.
            </p>
        </div>
    </footer>

    <script src="libs/jquery_1.11.3/jquery-1.11.3.js"></script>
    <script src="libs/bootstrap_3.3.5/js/bootstrap.min.js"></script>
    <script type="text/javascript">

        $(document).ready(function () {

            $("#register").submit(function(e) {

                var password = $("#reg-password").val();
                var password2 = $("#reg-password2").val();
                var error_msg = $("#error-msg");

                if(password != password2) {
                    error_msg.text("Passwords did not match. Please try again.");
                    error_msg.show();
                    $("html, body").animate({ scrollTop: error_msg.offset().top }, 'slow');
                    return false;
                }

                $.ajax("registration.do", {
                    async: false,
                    data: { is_validation: "true", reg_username: $("#reg-username").val() },
                    success: function(data) {
                        if($.trim(data) === "User Exist") {
                            error_msg.text("User already exist");
                            error_msg.show();
                            $("html, body").animate({ scrollTop: error_msg.offset().top }, 'slow');
                            e.preventDefault();
                        } else if ($.trim(data) === "Ok") {
                            return true;
                        } else {
                            var doc = document.open("text/html", "replace");
                            doc.write(data);
                            doc.close();
                            e.preventDefault();
                        }
                    },
                    error: function() {
                        error_msg.val("Unknown error occurred");
                        error_msg.show();
                        e.preventDefault();
                    }
                });
                return true;
            });
        });

        function forward() {
            location.href = "<%=Encode.forJavaScriptBlock(forwardTo)%>";
        }

    </script>
    </body>
    </html>
</fmt:bundle>

