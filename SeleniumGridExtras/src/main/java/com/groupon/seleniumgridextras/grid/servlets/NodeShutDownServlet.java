package com.groupon.seleniumgridextras.grid.servlets;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A simple servlet which basically issues a System.exit() when invoked.
 * This servlet would have to be injected into the node [not the Grid] so that it can help in terminating the node.
 *
 */
public class NodeShutDownServlet extends HttpServlet {

    private static final long serialVersionUID = -8308677302003045927L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
        shutdownNode();
    }

    protected void shutdownNode(){
        System.out.println("Shutting down the node");
        System.exit(0);
    }
}

