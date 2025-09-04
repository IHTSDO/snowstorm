package org.snomed.snowstorm.config;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StaticResourceExceptionResolver implements HandlerExceptionResolver {

	private static final int MAX_TRACKED_PATHS = 50;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final Set<String> recentPaths = new LinkedHashSet<>();

	private synchronized boolean addPath(String path) {
		boolean isNew = !recentPaths.contains(path);
		if (isNew) {
			recentPaths.add(path);
			// Keep only the last MAX_TRACKED_PATHS entries to avoid memory bloat / DOS
			if (recentPaths.size() > MAX_TRACKED_PATHS) {
				String first = recentPaths.iterator().next();
				recentPaths.remove(first);
			}
		}
		return isNew;
	}

	@Override
	public ModelAndView resolveException(
			@NotNull HttpServletRequest request,
			@NotNull HttpServletResponse response,
			Object handler,
			@NotNull Exception ex) {
		if (ex instanceof NoResourceFoundException) {
			String path = request.getRequestURI();

			// Log only once per path
			if (addPath(path)) {
				logger.warn("Request for non-existent static resource (returned 404): {}", path);
				logger.warn("Won't log any further failed requests for {}", path);
			}

			try {
				response.setStatus(HttpServletResponse.SC_NOT_FOUND); // return 404
			} catch (Exception ignored) {
				//No need to cause panic and alarm about someone requesting a non-existent resource.
			}
			return new ModelAndView(); // prevent default logging of exception
		}

		// Let other exceptions propagate normally
		return null;
	}
}

