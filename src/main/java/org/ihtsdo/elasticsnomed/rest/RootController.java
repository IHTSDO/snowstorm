package org.ihtsdo.elasticsnomed.rest;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
public class RootController {

	@RequestMapping(path = "/", method = RequestMethod.GET)
	public void getRoot(HttpServletResponse response) throws IOException {
		response.sendRedirect("swagger-ui.html");
	}

}
