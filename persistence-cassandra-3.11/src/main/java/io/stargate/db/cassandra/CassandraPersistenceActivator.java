package io.stargate.db.cassandra;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Hashtable;

import org.apache.cassandra.auth.CassandraAuthorizer;
import org.apache.cassandra.auth.PasswordAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.stargate.db.Persistence;
import io.stargate.db.cassandra.impl.CassandraPersistence;
import io.stargate.db.datastore.common.StargateConfigSnitch;
import io.stargate.db.datastore.common.StargateSeedProvider;

import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.ParameterizedClass;
import org.apache.cassandra.dht.Murmur3Partitioner;
import org.apache.cassandra.io.util.FileUtils;
import org.apache.cassandra.locator.SimpleSnitch;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class CassandraPersistenceActivator implements BundleActivator {

    private static final Logger logger = LoggerFactory.getLogger(CassandraPersistenceActivator.class);

    private Config makeConfig() throws IOException {
        Config c = new Config();

        //Throw away data directory since stargate is ephemeral anyway
        File baseDir = Files.createTempDirectory("stargate-cassandra").toFile();

        File commitLogDir = Paths.get(baseDir.getPath(), "commitlog").toFile();
        commitLogDir.mkdirs();

        File dataDir = Paths.get(baseDir.getPath(), "data").toFile();
        dataDir.mkdirs();

        File hintDir = Paths.get(baseDir.getPath(), "hints").toFile();
        hintDir.mkdirs();

        File cdcDir = Paths.get(baseDir.getPath(), "cdc").toFile();
        cdcDir.mkdirs();

        File cacheDir = Paths.get(baseDir.getPath(), "caches").toFile();
        cacheDir.mkdirs();

        //Add hook to cleanup
        FileUtils.deleteRecursiveOnExit(baseDir);

        String clusterName = System.getProperty("stargate.cluster_name", "stargate-cassandra");
        String listenAddress = System.getProperty("stargate.listen_address", InetAddress.getLocalHost().getHostAddress());
        Integer cqlPort = Integer.getInteger("stargate.cql_port", 9042);
        Integer listenPort = Integer.getInteger("stargate.seed_port", 7000);
        String seedList = System.getProperty("stargate.seed_list", "");
        String snitchClass = System.getProperty("stargate.snitch_classname", StargateConfigSnitch.class.getCanonicalName());

        if (snitchClass.equalsIgnoreCase("SimpleSnitch"))
            snitchClass = SimpleSnitch.class.getCanonicalName();

        if (snitchClass.equalsIgnoreCase("StargateConfigSnitch"))
            snitchClass = StargateConfigSnitch.class.getCanonicalName();

        String enableAuth = System.getProperty("stargate.enable_auth", "false");

        if (enableAuth.equalsIgnoreCase("true")) {
            c.authenticator = PasswordAuthenticator.class.getCanonicalName();
            c.authorizer = CassandraAuthorizer.class.getCanonicalName();
        }

        c.cluster_name = clusterName;
        c.num_tokens = 8;
        c.commitlog_sync = Config.CommitLogSync.periodic;
        c.commitlog_sync_period_in_ms = 10000;
        c.internode_compression = Config.InternodeCompression.none;
        c.commitlog_directory = commitLogDir.getAbsolutePath();
        c.hints_directory = hintDir.getAbsolutePath();
        c.cdc_raw_directory = cdcDir.getAbsolutePath();
        c.saved_caches_directory = cacheDir.getAbsolutePath();
        c.data_file_directories = new String[]{ dataDir.getAbsolutePath() };
        c.partitioner = Murmur3Partitioner.class.getCanonicalName();
        c.disk_failure_policy = Config.DiskFailurePolicy.best_effort;
        c.start_native_transport = false;
        c.native_transport_port = cqlPort;
        c.rpc_address = "0.0.0.0";
        c.broadcast_rpc_address = listenAddress;
        c.endpoint_snitch = snitchClass;
        c.storage_port = listenPort;
        c.listen_address = listenAddress;
        c.broadcast_address = listenAddress;
        c.start_rpc = false;
        c.seed_provider = new ParameterizedClass(
                StargateSeedProvider.class.getName(),
                Collections.singletonMap("seeds", seedList));

        return c;
    }


    @Override
    public void start(BundleContext context) {
        Persistence cassandraDB = new CassandraPersistence();
        Hashtable<String, String> props = new Hashtable<>();
        props.put("Identifier", "CassandraPersistence");

        try
        {
            cassandraDB.initialize(makeConfig());
        } catch (IOException e) {
            logger.error("Error initializing cassandra persistance", e);
            throw new IOError(e);
        }
        ServiceRegistration<?> registration = context.registerService(Persistence.class, cassandraDB, props);
    }

    @Override
    public void stop(BundleContext context) {
        // Do not need to unregister the service, because the OSGi framework will automatically do so
    }
}
