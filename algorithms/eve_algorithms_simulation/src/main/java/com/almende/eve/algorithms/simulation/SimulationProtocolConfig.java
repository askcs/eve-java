/*
 * Copyright: Almende B.V. (2014), Rotterdam, The Netherlands
 * License: The Apache Software License, Version 2.0
 */
package com.almende.eve.algorithms.simulation;

import com.almende.eve.protocol.ProtocolConfig;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class JSONRpcProtocolConfig.
 */
public class SimulationProtocolConfig extends ProtocolConfig {

	/**
	 * Instantiates a new JSON rpc protocol config.
	 */
	public SimulationProtocolConfig() {
		super();
		setClassName(SimulationProtocolBuilder.class.getName());
	}

	/**
	 * Instantiates a new JSON rpc protocol config.
	 * 
	 * @param node
	 *            the node
	 */
	public static SimulationProtocolConfig decorate(final ObjectNode node) {
		final SimulationProtocolConfig res = new SimulationProtocolConfig();
		res.copy(node);
		return res;
	}
	
}