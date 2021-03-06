/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.discovery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.TransportService;

import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyList;

/**
 * An implementation of {@link SeedHostsProvider} that reads hosts/ports
 * from the "discovery.seed_hosts" node setting. If the port is
 * left off an entry, we default to the first port in the {@code transport.port} range.
 *
 * An example setting might look as follows:
 * [67.81.244.10, 67.81.244.11:9305, 67.81.244.15:9400]
 */
public class SettingsBasedSeedHostsProvider implements SeedHostsProvider {

    private static final Logger logger = LogManager.getLogger(SettingsBasedSeedHostsProvider.class);

    public static final Setting<List<String>> DISCOVERY_SEED_HOSTS_SETTING =
        Setting.listSetting("discovery.seed_hosts", emptyList(), Function.identity(), Property.NodeScope);

    // these limits are per-address
    private static final int LIMIT_FOREIGN_PORTS_COUNT = 1;
    private static final int LIMIT_LOCAL_PORTS_COUNT = 5;

    private final List<String> configuredHosts;
    private final int limitPortCounts;


    public SettingsBasedSeedHostsProvider(Settings settings, TransportService transportService) {
        if (DISCOVERY_SEED_HOSTS_SETTING.exists(settings)) {
            configuredHosts = DISCOVERY_SEED_HOSTS_SETTING.get(settings);
            // we only limit to 1 address, makes no sense to ping 100 ports
            limitPortCounts = LIMIT_FOREIGN_PORTS_COUNT;
            checkInvalidPorts(configuredHosts);
        } else {
            // if unicast hosts are not specified, fill with simple defaults on the local machine
            configuredHosts = transportService.getLocalAddresses();
            limitPortCounts = LIMIT_LOCAL_PORTS_COUNT;
        }

        logger.debug("using initial hosts {}", configuredHosts);
    }

    public void checkInvalidPorts(List<String> configuredHosts) {
        for (String host : configuredHosts) {
            if (host.contains("-")) {
                throw new IllegalArgumentException("Configuration Setting discovery.seed_hosts does not support a range of ports, [" +
                                                    host + "] was found in provided seed hosts");
            }
        }
    }

    @Override
    public List<TransportAddress> getSeedAddresses(HostsResolver hostsResolver) {
        return hostsResolver.resolveHosts(configuredHosts, limitPortCounts);
    }
}
