package org.snomed.snowstorm.core.data.services;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * This class provides configuration for Web Routes to be set up, 
 * primarily for resolution of URIs based on SCTID (with optional module
 * and effective date) and routed to some service based on HTTP
 * accept headers.
 * @see docs/WebRouterConfiguration.md
 */
public class WebRouterConfigurationService {
	
	private static String FALL_BACK = "fall-back";
	
	private static String DEFAULT_ROUTE_INDICATOR = "all";
	
	private static String DEFAULT_NAMESPACE = "default";

	private static String NAMESPACE_MAPPING = "mapping";
	
	private static WebRouterConfigurationService singleton;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private Map<String, String> namespaceConfig = new HashMap<>();

	private Map<String, String> namespaceMap = new HashMap<>();

	//Map of namespaces to aliases to Webroutes
	private Map<String, Map<String, WebRoute>> webRoutesNamespaceMap;
	
	private WebRoute fallbackRoute;
	
	public static WebRouterConfigurationService instance() {
		return singleton;
	}

	public Map<String, String> getNamespaceConfig() {
		return namespaceConfig;
	}

	public Map<String, String> getNamespaceMap() {
		return namespaceMap;
	}
	
	public WebRoute getFallbackRoute () {
		//If we've no fallback route then we need to error out or we'll loop 
		if (fallbackRoute == null) {
			throw new IllegalStateException("Unable to suggest route to lookup namespace details, no fall-back route configured.");
		}
		return fallbackRoute;
	}

	@PostConstruct
	private void init() {
		webRoutesNamespaceMap = new HashMap<>();
		for (String key : namespaceConfig.keySet()) {
			if (key.equals(FALL_BACK)) {
				fallbackRoute = parseAcceptHeaderRoute(namespaceConfig.get(key));
				logger.info("Fall-back route set for use with unconfigured namespaces");
			} else if (key.equals(NAMESPACE_MAPPING)) {
				parseNamespaceMapping(namespaceConfig.get(key));
			} else {
				String[] namespaceAliasArr = key.split("\\.");
				if (namespaceAliasArr.length != 2) {
					throw new IllegalArgumentException(key + " malformed, expected uri.deferencing.namespace.<namespace>.<alias>");
				}
				
				String namespace = namespaceAliasArr[0];
				String alias = namespaceAliasArr[1];
				//Have we seen this namespace before?
				Map<String, WebRoute> webRouteAliases = webRoutesNamespaceMap.get(namespace);
				if (webRouteAliases == null) {
					webRouteAliases = new HashMap<String, WebRoute>();
					webRoutesNamespaceMap.put(namespace, webRouteAliases);
				}
				WebRoute webRoute = parseAcceptHeaderRoute(namespaceConfig.get(key));
				if (alias.equals(DEFAULT_ROUTE_INDICATOR)) {
					webRoute.setDefaultRoute(true);
				}
				webRouteAliases.put(alias, webRoute);
				logger.info("WebRoute " + alias + " " + webRoute + " configured for namespace: " + namespace);
			}
		}
		singleton = this;
	}

	private void parseNamespaceMapping(String namespaces) {
		if (StringUtils.hasLength(namespaces)) {
			String[] arr = namespaces.split(",");
			for (String str : arr) {
				String[] parts = str.split("\\|");
				if (parts.length == 2) {
					this.namespaceMap.put(parts[0], parts[1]);
				}
			}
		}
	}

	private WebRoute parseAcceptHeaderRoute(String acceptHeaderRoute) {
		String[] parts = acceptHeaderRoute.split("\\|");
		if (parts.length != 2) {
			throw new IllegalArgumentException("Unable to parse web route '" + acceptHeaderRoute + "' format expected: <accept-header>|<route template>");
		}
		return new WebRoute(parts[0], parts[1]);
	}
	
	public WebRoute lookup(String namespace, String acceptHeader, String alias) {
		//Do we have an entries for this namespace?  Will return default if null
		Map<String, WebRoute> webRoutes = webRoutesNamespaceMap.get(namespace);
		if (webRoutes == null) {
			//If we don't have a set of routes configured for this namespace, use that of the 
			//default namespace so we can look up locally incase the concept has been promoted
			//to the International Edition and we won't need to lookup the namespace in CIS
			//unless we can't find it here.
			webRoutes = webRoutesNamespaceMap.get(DEFAULT_NAMESPACE);
		}
		WebRoute defaultRoute = null;
		
		//Work through the various accept headers, using alias by preference
		for (Map.Entry<String, WebRoute> entry : webRoutes.entrySet()) {
			String thisAlias = entry.getKey();
			WebRoute route = entry.getValue();
			if (alias != null && thisAlias.equals(alias)) {
				return route;
			}
			
			if (alias == null && acceptHeader != null && route.getAcceptHeader().equals(acceptHeader)) {
				return route;
			}
			
			if (thisAlias.equals(DEFAULT_ROUTE_INDICATOR)) {
				defaultRoute = route;
			}
		}
		
		return defaultRoute;
	}

	public void report() {
		logger.info("Web router configuration complete with " + webRoutesNamespaceMap.size() + " namespace route sets configured");
	}
	
	public class WebRoute {
		private String acceptHeader;
		private String redirectionTemplate;
		private boolean isDefaultRoute = false;
		
		public boolean isDefaultRoute() {
			return isDefaultRoute;
		}

		public void setDefaultRoute(boolean isDefaultRoute) {
			this.isDefaultRoute = isDefaultRoute;
		}

		public WebRoute (String acceptHeader, String redirectionTemplate) {
			this.acceptHeader = acceptHeader;
			this.redirectionTemplate = redirectionTemplate;
		}
		
		public String getAcceptHeader() {
			return acceptHeader;
		}
		public void setAcceptHeader(String acceptHeader) {
			this.acceptHeader = acceptHeader;
		}
		public String getRedirectionTemplate() {
			return redirectionTemplate;
		}
		public void setRedirectionTemplate(String redirectionTemplate) {
			this.redirectionTemplate = redirectionTemplate;
		}
		public String toString() {
			return this.acceptHeader + " -> " + this.redirectionTemplate;
		}
	}

}
