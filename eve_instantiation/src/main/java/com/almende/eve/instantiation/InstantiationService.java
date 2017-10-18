/*
 * Copyright: Almende B.V. (2014), Rotterdam, The Netherlands
 * License: The Apache Software License, Version 2.0
 */
package com.almende.eve.instantiation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.almende.eve.capabilities.Capability;
import com.almende.eve.capabilities.handler.Handler;
import com.almende.eve.state.State;
import com.almende.eve.state.StateBuilder;
import com.almende.eve.state.StateConfig;
import com.almende.eve.state.StateService;
import com.almende.util.TypeUtil;
import com.almende.util.jackson.JOM;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static java.lang.management.ManagementFactory.*;

/**
 * The Class InstantiationService.
 */
public class InstantiationService implements Capability {
	private static final Logger							LOG					= Logger.getLogger(InstantiationService.class
																					.getName());
	private static final TypeUtil<InstantiationEntry>	INSTANTIATIONENTRY	= new TypeUtil<InstantiationEntry>() {};
	private ObjectNode									myParams			= null;
	private String										myId				= null;
	private Map<String, InstantiationEntry>				entries				= new HashMap<String, InstantiationEntry>();
	private StateService								stateService		= null;
	private ClassLoader									cl					= null;

	/**
	 * Instantiates a new wake service.
	 */
	public InstantiationService() {}

	/**
	 * Instantiates a new InstantiationService.
	 *
	 * @param params
	 *            the params, containing at least a "state" field, with a
	 *            specific State configuration.
	 * @param cl
	 *            the cl
	 */
	public InstantiationService(final ObjectNode params, final ClassLoader cl) {
		this.cl = cl;
		myParams = params;

		final InstantiationServiceConfig config = InstantiationServiceConfig
				.decorate(params);
		final State state = new StateBuilder().withConfig(config.getState())
				.build();
		stateService = state.getService();
		myId = state.getId();
		InstantiationServiceBuilder.getServices().put(myId, this);
		load();
	}

	@Override
	public void delete() {
		// TODO: clear out all state files
		final State state = new StateBuilder().withConfig(
				(ObjectNode) myParams.get("state")).build();
		if (state != null) {
			state.delete();
		}
	}

	/**
	 * Gets the my params.
	 *
	 * @return the my params
	 */
	public ObjectNode getMyParams() {
		return myParams;
	}

	/**
	 * Sets the my params.
	 *
	 * @param myParams
	 *            the new my params
	 */
	public void setMyParams(final ObjectNode myParams) {
		this.myParams = myParams;
		load();
	}

	/**
	 * Boot.
	 */
	@JsonIgnore
	public void boot() {
        load();

        Set<String> domainAgents = new HashSet<>();
        domainAgents.add("ask_mgmt");
        domainAgents.add("restagent");

        for (String key : entries.keySet()) {

            boolean isDomainAgent = key != null && key.length() > 11 && key.endsWith("_groupAgent") && entries.containsKey(key.substring(0, key.length() - 11));
            if (isDomainAgent) {
                domainAgents.add(key.substring(0, key.length() - 11));
                domainAgents.add(key);
            }

            if ("restagent".equals(key)) {
                domainAgents.add(key);
            }
        }

        int count = 0;
        for (String key : domainAgents) {
            Object res = init(key, true);
        }

        long jvmUpTime = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        LOG.info("Booted " + count + " agents (@ " + (jvmUpTime / 1000.0) + "sec)");

        //load agents in a separate thread
        DeferredBoot db = new DeferredBoot();
        db.exclude = domainAgents;
        db.start();
	}

    public synchronized Configurable boot_key(String key, boolean onBoot) {

	    if (!entries.keySet().contains(key)) {
            System.err.println("no such key: " + key);
            return null;
        }

        if (entries.get(key) != null) {
            return null;
        }

        return init(key, onBoot);
    }

    /**
	 * Exists.
	 *
	 * @param wakeKey
	 *            the wake key
	 * @return true, if successful
	 */
	public boolean exists(final String wakeKey) {
		return entries.containsKey(wakeKey);
	}

	/**
	 * Init a specific initable.
	 *
	 * @param wakeKey
	 *            the wake key
	 * @return the initable
	 */
	public Configurable init(final String wakeKey) {
		return init(wakeKey, false);
	}

	/**
	 * Wake.
	 *
	 * @param wakeKey
	 *            the wake key
	 * @param onBoot
	 *            the on boot
	 * @return the initable
	 */
	@JsonIgnore
	public Configurable init(final String wakeKey, final boolean onBoot) {

        System.out.println(String.format("InstantiationService::init key: %s, onBoot: %s", wakeKey, onBoot));

        InstantiationEntry entry = entries.get(wakeKey);
		if (entry == null) {
			entry = load(wakeKey);
			entries.put(wakeKey, entry);
		}
		if (entry != null) {
			final String className = entry.getClassName();
			Configurable instance = null;
			Handler<Object> oldHandler = entry.getHandler();
			if (oldHandler != null) {
				final Object object = oldHandler.getNoWait();
				if (object != null && object instanceof Configurable) {
					instance = (Configurable) object;
				}
			}
			if (instance == null) {
				try {
					Class<?> clazz = null;
					if (cl != null) {
						clazz = cl.loadClass(className);
					} else {
						clazz = Class.forName(className);
					}
					instance = (Configurable) clazz.newInstance();
					instance.setConfig(entry.getParams());
				} catch (final Exception e) {
					LOG.log(Level.WARNING, "Failed to instantiate entry:'"
							+ wakeKey + "'", e);
				}
			}
			if (instance != null) {
				entry.setHandler(instance.getHandler());
				if (oldHandler != null) {
					oldHandler.update(instance.getHandler());
				}
				entries.put(wakeKey, entry);
			}
			return instance;
		} else {
			LOG.warning("Sorry, I don't know any entry called:'" + wakeKey
					+ "'");
		}
		return null;
	}

	/**
	 * Register.
	 *
	 * @param wakeKey
	 *            the wake key
	 * @param className
	 *            the class name
	 */
	@JsonIgnore
	public void register(final String wakeKey, final String className) {
		final InstantiationEntry entry = new InstantiationEntry(wakeKey, null,
				className);
		entries.put(wakeKey, entry);
		store();
	}

	/**
	 * Register.
	 *
	 * @param wakeKey
	 *            the wake key
	 * @param params
	 *            the params
	 * @param className
	 *            the class name
	 */
	@JsonIgnore
	public void register(final String wakeKey, final ObjectNode params,
			final String className) {
		final InstantiationEntry entry = new InstantiationEntry(wakeKey,
				params, className);
		entries.put(wakeKey, entry);
		store(wakeKey, entry);
	}

	/**
	 * Deregister.
	 *
	 * @param wakeKey
	 *            the wake key
	 */
	public void deregister(final String wakeKey) {
		final InstantiationEntry entry = entries.remove(wakeKey);
		remove(wakeKey, entry);
	}

	/**
	 * Store.
	 *
	 * @param key
	 *            the key
	 * @param val
	 *            the val
	 */
	private void store(final String key, final InstantiationEntry val) {
		State innerState = null;
		if (val != null) {
			innerState = val.getState();
		}
		if (innerState == null) {
			innerState = new StateBuilder().withConfig(
					StateConfig.decorate((ObjectNode) myParams.get("state"))
							.put("id", key)).build();
		}
		if (innerState != null) {
			innerState.put("entry", JOM.getInstance().valueToTree(val));
		}
	}

	/**
	 * Removes the specific state.
	 *
	 * @param key
	 *            the key
	 * @param val
	 *            the val
	 */
	private void remove(final String key, final InstantiationEntry val) {
		State innerState = null;
		if (val != null) {
			innerState = val.getState();
		}
		if (innerState == null) {
			innerState = new StateBuilder().withConfig(
					StateConfig.decorate((ObjectNode) myParams.get("state"))
							.put("id", key)).build();
		}
		if (innerState != null) {
			innerState.delete();
		}
	}

	/**
	 * Load.
	 *
	 * @param key
	 *            the key
	 * @return the instantiation entry
	 */
	private InstantiationEntry load(final String key) {
		final State innerState = new StateBuilder().withConfig(
				StateConfig.decorate((ObjectNode) myParams.get("state")).put(
						"id", key)).build();
		final InstantiationEntry result = innerState.get("entry",
				INSTANTIATIONENTRY);
		if (result != null) {
			result.setState(innerState);
		}
		return result;
	}

	/**
	 * Store.
	 */
	private void store() {
		for (Entry<String, InstantiationEntry> entry : entries.entrySet()) {
			if (entry.getValue() != null) {
				store(entry.getKey(), entry.getValue());
			}
		}
	}

	/**
	 * Load.
	 */
	private void load() {
		final Set<String> stateIds = stateService.getStateIds();
		for (String key : stateIds) {
			if (key.equals(myId)) {
				continue;
			}
			entries.put(key, null);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.almende.eve.capabilities.Capability#getParams()
	 */
	@Override
	public ObjectNode getParams() {
		return myParams;
	}

	class DeferredBoot extends Thread {
		Set<String> exclude = null;

		@Override
		public void run() {
			boot();
		}

		void boot() {

			if (entries == null) {
				System.out.println("No agents to boot!");
				return;
			}

			int count = 0;
			int skipped = 0;
			int total = entries.size();

			Set<String> entriesKeys = new HashSet<>(entries.keySet());
			Set<String> secondKeys = new HashSet<>();

			LOG.info("----------------------------------");
			LOG.info(String.format("Deferred boot: booting %s agents (delaying MessageAgent and NotificationAgent for second boot round)", entriesKeys.size()));
			LOG.info("Uptime: " + getUptime());
			for (String key : entriesKeys) {

				if (exclude != null && exclude.contains(key)) {
					skipped++;
					continue;
				}

				if (key == null || key.contains("{")) {
					System.err.println(String.format("Not booting agent with suspicious id: %s", key));
					skipped++;
					continue;
				}

				if (key.startsWith("notificationAgent_") || key.startsWith("messageAgent_")) {
					secondKeys.add(key);
					continue;
				}

				Object res = InstantiationService.this.boot_key(key, true);
				if (res != null) {
					count++;
				} else {
					skipped++;
				}

				if (count % 100 == 0) {
					System.out.println(String.format("Booted %s of %s agents", count, total));
					LOG.info("Uptime: " + getUptime());
				}
			}

			LOG.info("----------------------------------");
			LOG.info("");
			LOG.info(String.format("Deferred boot: done with first round. Booted %s agents", count));
			LOG.info("Uptime: " + getUptime());
			LOG.info(String.format("Deferred boot: starting round 2 (MessageAgent and NotificationAgent). Booting %s agents", secondKeys.size()));
			LOG.info("");

			for (String key : secondKeys) {

				Object res = InstantiationService.this.init(key, true);
				if (res != null) {
					count++;
				}

				if (count % 100 == 0) {
					System.out.println(String.format("Booted %s of %s agents", count, total));
				}
			}

			LOG.info("----------------------------------");
			LOG.info("");
			LOG.info(String.format("Deferred boot: done with round 2. Total booted %s agents", count));
			LOG.info("Uptime: " + getUptime());
		}

		long getUptime() {
			return getRuntimeMXBean().getUptime();
		}
	}
}
