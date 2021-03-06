/*
 * Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.hadoop.hoya.yarn.appmaster;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hoya.providers.ClusterExecutor;
import org.apache.hadoop.hoya.providers.ProviderUtils;
import org.apache.hadoop.hoya.providers.hbase.HBaseCommands;
import org.apache.hadoop.hoya.HoyaKeys;
import org.apache.hadoop.hoya.api.ClusterDescription;
import org.apache.hadoop.hoya.api.ClusterNode;
import org.apache.hadoop.hoya.tools.HoyaUtils;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.security.ContainerTokenIdentifier;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.hadoop.yarn.util.Records;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thread that runs on the AM to launch a region server.
 */
public class RoleLauncher implements Runnable {
  protected static final Logger log =
    LoggerFactory.getLogger(RoleLauncher.class);

  private final HoyaAppMaster owner;

  // Allocated container
  private final Container container;
  private final String containerRole;
  private final Map<String, String> roleOptions;
  private final ClusterExecutor provider;

  public RoleLauncher(HoyaAppMaster owner,
                      Container container,
                      String role,
                      ClusterExecutor provider,
                      Map<String, String> roleOptions) {
    assert owner != null;
    assert container != null;
    assert role != null;
    assert roleOptions != null;
    assert provider != null;
    this.owner = owner;
    this.container = container;
    this.containerRole = role;
    this.roleOptions = roleOptions;
    this.provider = provider;
  }

  @Override
  public void run() {

    try {
      UserGroupInformation user =
        UserGroupInformation.createRemoteUser(container.getId().toString());
      String cmIpPortStr =
        container.getNodeId().getHost() + ":" + container.getNodeId().getPort();
      final InetSocketAddress cmAddress =
        NetUtils.createSocketAddr(cmIpPortStr);

      Token<ContainerTokenIdentifier> token =
        ConverterUtils.convertFromYarn(container.getContainerToken(),
                                       cmAddress);
      user.addToken(token);

      log.debug("Launching container {} into role {}",
                container.getId(),
                containerRole);
      FileSystem fs = owner.getClusterFS();
      Path generatedConfPath = new Path(owner.getDFSConfDir());

      ContainerLaunchContext ctx = Records
        .newRecord(ContainerLaunchContext.class);
      //now build up the configuration data    
      provider.buildContainerLaunchContext(ctx, fs, generatedConfPath, containerRole);

      // Set the environment
      Map<String, String> env = HoyaUtils.buildEnvMap(roleOptions);
      env.put(HoyaKeys.HBASE_LOG_DIR,
              new ProviderUtils(HoyaAppMaster.log).getLogdir());

      ctx.setEnvironment(env);
      //local resources
      Map<String, LocalResource> localResources =
        new HashMap<String, LocalResource>();

      //add the configuration resources
      Map<String, LocalResource> confResources;
      confResources = HoyaUtils.submitDirectory(fs,
                                                generatedConfPath,
                                                HoyaKeys.PROPAGATED_CONF_DIR_NAME);
      localResources.putAll(confResources);

      //Add binaries
      ClusterDescription clusterSpec = owner.clusterDescription;
      //now add the image if it was set
      if (clusterSpec.imagePath != null) {
        Path imagePath = new Path(clusterSpec.imagePath);
        log.info("using image path {}", imagePath);
        HoyaUtils.maybeAddImagePath(fs, localResources, imagePath);
      }
      ctx.setLocalResources(localResources);

      List<String> command = new ArrayList<String>();
      //this must stay relative if it is an image
      command.add(owner.buildHBaseBinPath(clusterSpec).toString());

      //config dir is relative to the generated file
      command.add(HBaseCommands.ARG_CONFIG);
      command.add(HoyaKeys.PROPAGATED_CONF_DIR_NAME);
      //role is region server
      command.add(HBaseCommands.REGION_SERVER);
      command.add(HBaseCommands.ACTION_START);

      //log details
      command.add(
        "1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/out.txt");
      command.add(
        "2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/err.txt");

      String cmdStr = HoyaUtils.join(command, " ");

      List<String> commands = new ArrayList<String>();
      commands.add(cmdStr);
      ctx.setCommands(commands);
      log.info("Starting container with command: {}", cmdStr);

      ClusterNode node = new ClusterNode();
      List<String> nodeEnv = new ArrayList<String>();
      for (Map.Entry<String, LocalResource> entry : localResources.entrySet()) {

        String key = entry.getKey();
        LocalResource val = entry.getValue();
        String envElt = key + "=" + HoyaUtils.stringify(val.getResource());
        nodeEnv.add(envElt);
        log.info(envElt);
      }
      node.command = cmdStr;
      node.name = container.getId().toString();
      node.role = containerRole;
      node.environment = nodeEnv.toArray(new String[nodeEnv.size()]);
      owner.startContainer(container, ctx, node);
    } catch (IOException e) {
      log.error("Exception thrown while trying to start "+containerRole + ": " + e,
                e);
    } finally {
      owner.launchedThreadCompleted(this);
    }
  }

}
