package com.kaicube.snomed.elasticsnomed;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.UnknownHostException;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

@Configuration
public class TestConfig extends App {

	@Bean // Use embedded Elastic search Server
	public Client client() throws UnknownHostException {
		return nodeBuilder().local(true).settings(Settings.builder().put("path.home", "target/data").build()).node().client();
	}

}
