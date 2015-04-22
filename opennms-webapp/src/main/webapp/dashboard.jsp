<%--
/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2002-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

--%>

<%@page language="java" contentType="text/html" session="true" %>

<%--
/*******************************************************************************
 * Check org.opennms.dashboard.implementation for selected implementation      *
 *******************************************************************************/
--%>

<%
    String dashboardImplementation = System.getProperty("org.opennms.dashboard.implementation", "vaadin").trim();

    if (!"gwt".equals(dashboardImplementation)) {
%>

<%--
/*******************************************************************************
 * Include VAADIN implementation                                               *
 *******************************************************************************/
--%>

<jsp:include page="/includes/bootstrap.jsp" flush="false" >
    <jsp:param name="title" value="Dashboard" />
    <jsp:param name="headTitle" value="Dashboard" />
    <jsp:param name="location" value="dashboard" />
    <jsp:param name="vaadinEmbeddedStyles" value="true" />
    <jsp:param name="breadcrumb" value="Dashboard" />
</jsp:include>

<%
    String viewName = "";

    if (request.getParameterMap().containsKey("viewName")) {
        viewName = "&viewName=" + request.getParameter("viewName");
    }
%>

<div class="row" style="height:95%">
    <div class="col-md-10" style="height:100%">
        <iframe name="dashboard" id="surveillance-view-ui" src="osgi/vaadin-surveillance-views?dashboard=true<%= viewName %>" frameborder="0" style="height:100%; width:100%;"></iframe>
    </div>
    <div class="col-md-2">
        <div class="resource-graphs-sidebar hidden-print hidden-xs hidden-sm sidebar-fixed">
            <ul class="nav nav-stacked">
                <li>
                    <a target="dashboard" href="osgi/vaadin-surveillance-views?dashboard=true<%= viewName %>#surveillanceview" data-target="#surveillanceview">Surveillance View</a>
                </li>
                <li>
                    <a target="dashboard" href="osgi/vaadin-surveillance-views?dashboard=true<%= viewName %>#alarms" data-target="#alarms">Alarms</a>
                </li>
                <li>
                    <a target="dashboard" href="osgi/vaadin-surveillance-views?dashboard=true<%= viewName %>#notifications">Notifications</a>
                </li>
                <li>
                    <a target="dashboard" href="osgi/vaadin-surveillance-views?dashboard=true<%= viewName %>#outages">Outages</a>
                </li>
                <li>
                    <a target="dashboard" href="osgi/vaadin-surveillance-views?dashboard=true<%= viewName %>#resourcegraphs">Resource Graphs</a>
                </li>
            </ul>
        </div>
    </div>
</div>

<jsp:include page="/includes/bootstrap-footer.jsp" flush="true"/>

<% } else { %>

<%--
/*******************************************************************************
 * Include GWT implementation                                                  *
 *******************************************************************************/
--%>

<jsp:include page="/includes/bootstrap.jsp" flush="false">
    <jsp:param name="title" value="Dashboard" />
    <jsp:param name="headTitle" value="Dashboard" />
    <jsp:param name="location" value="dashboard" />
    <jsp:param name="breadcrumb" value="Dashboard" />
    <jsp:param name="meta">
            <jsp:attribute name="value">
    	        <meta name='gwt:module' content='org.opennms.dashboard.Dashboard' />
	        </jsp:attribute>
    </jsp:param>
    <jsp:param name="meta">
	        <jsp:attribute name="value">
                <link media="screen" href="css/dashboard.css" type="text/css" rel="stylesheet">
	        </jsp:attribute>
    </jsp:param>

</jsp:include>

<script type="text/javascript" src='dashboard/dashboard.nocache.js'></script>

<div class="row">
    <div class="col-md-12">
        <div class="row">
            <div class="col-md-12">
                <div class="dashletCell"id="surveillanceView"></div>
            </div>
        </div>

        <div class="row">
            <div class="col-md-12">
                <div class="dashletCell" id="alarms"></div>
            </div>
        </div>

        <div class="row">
            <div class="col-md-12">
                <div class="dashletCell" id="notifications"></div>
            </div>
        </div>

        <div class="row">
            <div class="col-md-12">
                <div class="dashletCell" id="nodeStatus"></div>
            </div>
        </div>

        <div class="row">
            <div class="col-md-12">
                <div class="dashletCell" id="graphs"></div>
            </div>
        </div>

        <div class="row">
            <div class="col-md-12">
                <div class="dashletCell" id="outages"></div>
            </div>
        </div>
    </div>
</div>

<jsp:include page="/includes/bootstrap-footer.jsp" flush="false" />

<% } %>


