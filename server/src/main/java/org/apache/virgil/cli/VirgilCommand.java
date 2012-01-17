package org.apache.virgil.cli;

import org.apache.cassandra.thrift.CassandraDaemon;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.thrift.transport.TTransportException;
import org.apache.virgil.CassandraStorage;
import org.apache.virgil.VirgilService;
import org.apache.virgil.config.VirgilConfiguration;
import org.apache.virgil.index.SolrIndexer;

import com.yammer.dropwizard.AbstractService;
import com.yammer.dropwizard.cli.ServerCommand;

public class VirgilCommand extends ServerCommand<VirgilConfiguration> {
	public VirgilCommand(String name) {
		super(VirgilConfiguration.class);
	}

	@SuppressWarnings("static-access")
	@Override
	public Options getOptions() {
		Options options = new Options();
		OptionGroup runMode = new OptionGroup();
		Option host = OptionBuilder.withArgName("h").hasArg().withDescription("Host name for Cassandra.")
				.create("host");
		Option embedded = OptionBuilder.withArgName("e").withDescription("Run in embedded mode").create("embedded");
		runMode.addOption(host);
		runMode.addOption(embedded);
		options.addOptionGroup(runMode);		
		return options;
	}

	private CassandraStorage createCassandraStorage(CommandLine params, VirgilConfiguration config)
			throws TTransportException {
		SolrIndexer indexer = new SolrIndexer(config);

		if (params.hasOption("embedded")) {
			System.out.println("Starting virgil with embedded cassandra server.");
			String yamlFile = config.getCassandraYaml();
			if (yamlFile == null)
				yamlFile = "cassandra.yaml";
			System.setProperty("cassandra.config", yamlFile);
			System.setProperty("cassandra-foreground", "true");
			System.setProperty(VirgilConfiguration.CASSANDRA_PORT_PROPERTY, "9160");
			System.setProperty(VirgilConfiguration.CASSANDRA_HOST_PROPERTY, "localhost");
			CassandraDaemon.main(null);
			return new CassandraStorage("localhost", 9160, config, indexer, true);
		} else {
			String cassandraHost = params.getOptionValue("host");
			if (cassandraHost == null)
				throw new RuntimeException("Need to specify a host if not running in embedded mode. (-e)");
			System.setProperty(VirgilConfiguration.CASSANDRA_HOST_PROPERTY, cassandraHost);
			String cassandraPort = params.getOptionValue("port");
			if (cassandraPort == null)
				cassandraPort = "9160";
			System.setProperty(VirgilConfiguration.CASSANDRA_PORT_PROPERTY, cassandraPort);
			System.out.println("Starting virgil against remote cassandra server [" + cassandraHost + ":"
					+ cassandraPort + "]");
			return new CassandraStorage(cassandraHost, Integer.parseInt(cassandraPort), config, indexer, false);
		}
	}

	@Override
	protected void run(AbstractService<VirgilConfiguration> service, VirgilConfiguration config, CommandLine params)
			throws Exception {
		assert (service instanceof VirgilService);
		VirgilService virgil = (VirgilService) service;
		CassandraStorage storage = this.createCassandraStorage(params, config);
		virgil.setStorage(storage);
		virgil.setConfig(config);
		super.run(service, config, params);
	}
}
