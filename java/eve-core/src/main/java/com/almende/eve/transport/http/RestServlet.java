package com.almende.eve.transport.http;

import java.io.IOException;
import java.util.Enumeration;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.eve.agent.AgentHostDefImpl;
import com.almende.eve.agent.callback.CallbackInterface;
import com.almende.eve.agent.callback.SyncCallback;
import com.almende.eve.rpc.jsonrpc.JSONRequest;
import com.almende.eve.rpc.jsonrpc.JSONResponse;
import com.almende.util.TypeUtil;
import com.almende.util.uuid.UUID;


@SuppressWarnings("serial")
public class RestServlet extends HttpServlet {
	private Logger logger = Logger.getLogger(this.getClass().getSimpleName());
	private AgentHostDefImpl host = null;
	
	@Override
	public void init() {
		if (AgentHostDefImpl.getInstance().getStateFactory() == null){
			logger.severe("DEPRECIATED SETUP: Please add com.almende.eve.transport.http.AgentListener as a Listener to your web.xml!");
			AgentListener.init(getServletContext());
		}
		host = AgentHostDefImpl.getInstance();
	}
	
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		try {
			// get method from url
			String uri = req.getRequestURI();
			String[] path = uri.split("\\/");
			String agentId = (path.length > 2) ? path[path.length - 2] : null;
			String method = (path.length > 1) ? path[path.length - 1] : null;

			// get query parameters
			JSONRequest request = new JSONRequest();
			request.setMethod(method);
			Enumeration<String> params = req.getParameterNames();
			while (params.hasMoreElements()) {
				String param = params.nextElement();
				request.putParam(param, req.getParameter(param));
			}

			String tag = new UUID().toString();
			SyncCallback<Object> callback = new SyncCallback<Object>();
			
			CallbackInterface<Object> callbacks = host.getCallbackService("HttpTransport",Object.class);
			callbacks.store(tag,callback);
			
			String senderUrl = null;
			host.receive(agentId, request, senderUrl, tag);

			JSONResponse response = TypeUtil.inject(callback.get(),JSONResponse.class);
			
			// return response
			resp.addHeader("Content-Type", "application/json");
			resp.getWriter().println(response.getResult());
		} catch (Exception err) {
			resp.getWriter().println(err.getMessage());
		}
	}

	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		doGet(req, resp);
	}
}
