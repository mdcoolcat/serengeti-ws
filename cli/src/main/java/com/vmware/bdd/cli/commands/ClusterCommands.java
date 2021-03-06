/*****************************************************************************
 *      Copyright (c) 2012 VMware, Inc. All Rights Reserved.
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 ****************************************************************************/
package com.vmware.bdd.cli.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jline.ConsoleReader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.roo.shell.CliAvailabilityIndicator;
import org.springframework.roo.shell.CliCommand;
import org.springframework.roo.shell.CliOption;
import org.springframework.roo.shell.CommandMarker;
import org.springframework.stereotype.Component;

import com.vmware.bdd.apitypes.ClusterCreate;
import com.vmware.bdd.apitypes.ClusterRead;
import com.vmware.bdd.apitypes.DistroRead;
import com.vmware.bdd.apitypes.NetworkRead;
import com.vmware.bdd.apitypes.NodeGroupCreate;
import com.vmware.bdd.apitypes.NodeGroupRead;
import com.vmware.bdd.apitypes.NodeRead;
import com.vmware.bdd.cli.rest.CliRestException;
import com.vmware.bdd.cli.rest.ClusterRestClient;
import com.vmware.bdd.cli.rest.DistroRestClient;
import com.vmware.bdd.cli.rest.NetworkRestClient;

@Component
public class ClusterCommands implements CommandMarker {
   @Autowired
   private DistroRestClient distroRestClient;

   @Autowired
   private NetworkRestClient networkRestClient;

   @Autowired
   private ClusterRestClient restClient;

   //define role of the node group .
   private enum NodeGroupRole {
      MASTER, WORKER, CLIENT, NONE
   }

   @CliAvailabilityIndicator({ "cluster help" })
   public boolean isCommandAvailable() {
      return true;
   }

   @CliCommand(value = "cluster create", help = "Create a default vhadoop cluster")
   public void createCluster(
         @CliOption(key = { "name" }, mandatory = true, help = "The cluster name") final String name,
         @CliOption(key = { "distro" }, mandatory = false, help = "Hadoop Distro") final String distro,
         @CliOption(key = { "specFile" }, mandatory = false, help = "The spec file name path") final String specFilePath,
         @CliOption(key = { "rpNames" }, mandatory = false, help = "Resource Pools for the cluster: use \",\" among names.") final String rpNames,
         @CliOption(key = { "dsNames" }, mandatory = false, help = "Datastores for the cluster: use \",\" among names.") final String dsNames,
         @CliOption(key = { "networkName" }, mandatory = false, help = "Network Name") final String networkName,
         @CliOption(key = { "resume" }, mandatory = false, specifiedDefaultValue = "true", unspecifiedDefaultValue = "false", help = "flag to resume cluster creation") final boolean resume) {
      //validate the name
      if (name.indexOf("-") != -1) {
         CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_CREATE, Constants.OUTPUT_OP_RESULT_FAIL,
               Constants.PARAM_CLUSTER
                     + Constants.PARAM_NOT_CONTAIN_HORIZONTAL_LINE);

         return;
      }

      //process resume
      if (resume) {
         resumeCreateCluster(name);
         return;
      }

      // build ClusterCreate object
      ClusterCreate clusterCreate = new ClusterCreate();
      clusterCreate.setName(name);
      if (distro != null) {
         List<String> distroNames = getDistroNames();
         if (validName(distro, distroNames)) {
            clusterCreate.setDistro(distro);
         } else {
            CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER,
                  name, Constants.OUTPUT_OP_CREATE,
                  Constants.OUTPUT_OP_RESULT_FAIL, Constants.PARAM_DISTRO
                        + Constants.PARAM_NOT_SUPPORTED + distroNames);
            return;
         }
      }

      if (rpNames != null) {
         List<String> rpNamesList = CommandsUtils.inputsConvert(rpNames);
         if (rpNamesList.isEmpty()) {
            CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER,
                  name, Constants.OUTPUT_OP_CREATE,
                  Constants.OUTPUT_OP_RESULT_FAIL,
                  Constants.INPUT_RPNAMES_PARAM + Constants.MULTI_INPUTS_CHECK);
            return;
         } else {
            clusterCreate.setRpNames(rpNamesList);
         }
      }
      if (dsNames != null) {
         List<String> dsNamesList = CommandsUtils.inputsConvert(dsNames);
         if (dsNamesList.isEmpty()) {
            CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER,
                  name, Constants.OUTPUT_OP_CREATE,
                  Constants.OUTPUT_OP_RESULT_FAIL,
                  Constants.INPUT_DSNAMES_PARAM + Constants.MULTI_INPUTS_CHECK);
            return;
         } else {
            clusterCreate.setDsNames(dsNamesList);
         }
      }
      try {
         if (specFilePath != null) {
            clusterCreate.setNodeGroupCreates(CommandsUtils
                  .getObjectByJsonString(NodeGroupCreate[].class,
                        CommandsUtils.dataFromFile(specFilePath)));
         }
      } catch (Exception e) {
         CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_CREATE, Constants.OUTPUT_OP_RESULT_FAIL,
               e.getMessage());
         return;
      }

      List<String> networkNames = getNetworkNames();

      if (networkNames.isEmpty()) {
         CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_CREATE, Constants.OUTPUT_OP_RESULT_FAIL,
               Constants.PARAM_NETWORK_NAME + Constants.PARAM_NOT_EXISTED);
         return;
      } else {
         if (networkName != null) {
            if (validName(networkName, networkNames)) {
               clusterCreate.setNetworkName(networkName);
            } else {
               CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER,
                     name, Constants.OUTPUT_OP_CREATE,
                     Constants.OUTPUT_OP_RESULT_FAIL,
                     Constants.PARAM_NETWORK_NAME
                           + Constants.PARAM_NOT_SUPPORTED + networkNames);
               return;
            }
         } else {
            if (networkNames.size() == 1) {
               clusterCreate.setNetworkName(networkNames.get(0));
            } else {
               CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER,
                     name, Constants.OUTPUT_OP_CREATE,
                     Constants.OUTPUT_OP_RESULT_FAIL,
                     Constants.PARAM_NETWORK_NAME
                           + Constants.PARAM_NOT_SPECIFIED);
               return;
            }
         }
      }

      // Validate that the specified file is correct json format and proper value.
      if (specFilePath != null) {
         if (!validateClusterCreate(clusterCreate)) {
            return;
         }
      }

      // rest invocation
      try {
         restClient.create(clusterCreate);
         CommandsUtils.printCmdSuccess(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_RESULT_CREAT);
      } catch (CliRestException e) {
         CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_CREATE, Constants.OUTPUT_OP_RESULT_FAIL,
               e.getMessage());
      }
   }

   @CliCommand(value = "cluster list", help = "Get cluster information")
   public void getCluster(
         @CliOption(key = { "name" }, mandatory = false, help = "The cluster name") final String name,
         @CliOption(key = { "detail" }, mandatory = false, specifiedDefaultValue = "true", unspecifiedDefaultValue = "false", help = "flag to show node information") final boolean detail) {

      // rest invocation
      try {
         if (name == null) {
            ClusterRead[] clusters = restClient.getAll();
            if (clusters != null) {
               prettyOutputClustersInfo(clusters, detail);
            }
         } else {
            ClusterRead cluster = restClient.get(name);
            if (cluster != null) {
               prettyOutputClusterInfo(cluster, detail);
            }
         }
      } catch (CliRestException e) {
         CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_LIST, Constants.OUTPUT_OP_RESULT_FAIL,
               e.getMessage());
      }
   }

   @CliCommand(value = "cluster delete", help = "Delete a cluster")
   public void deleteCluster(
         @CliOption(key = { "name" }, mandatory = true, help = "The cluster name") final String name) {

      //rest invocation
      try {
         restClient.delete(name);
         CommandsUtils.printCmdSuccess(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_RESULT_DELETE);
      } catch (CliRestException e) {
         CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_DELETE, Constants.OUTPUT_OP_RESULT_FAIL,
               e.getMessage());
      }
   }

   @CliCommand(value = "cluster start", help = "Start a cluster")
   public void startCluster(
         @CliOption(key = { "name" }, mandatory = true, help = "The cluster name") final String name) {

      Map<String, String> queryStrings = new HashMap<String, String>();
      queryStrings
            .put(Constants.QUERY_ACTION_KEY, Constants.QUERY_ACTION_START);

      //rest invocation
      try {
         restClient.actionOps(name, queryStrings);
         CommandsUtils.printCmdSuccess(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_RESULT_START);
      } catch (CliRestException e) {
         CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_START, Constants.OUTPUT_OP_RESULT_FAIL,
               e.getMessage());
      }
   }

   @CliCommand(value = "cluster stop", help = "Stop a cluster")
   public void stopCluster(
         @CliOption(key = { "name" }, mandatory = true, help = "The cluster name") final String name) {
      Map<String, String> queryStrings = new HashMap<String, String>();
      queryStrings.put(Constants.QUERY_ACTION_KEY, Constants.QUERY_ACTION_STOP);

      //rest invocation
      try {
         restClient.actionOps(name, queryStrings);
         CommandsUtils.printCmdSuccess(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_RESULT_STOP);
      } catch (CliRestException e) {
         CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_STOP, Constants.OUTPUT_OP_RESULT_FAIL,
               e.getMessage());
      }
   }

   @CliCommand(value = "cluster resize", help = "Resize a cluster")
   public void resizeCluster(
         @CliOption(key = { "name" }, mandatory = true, help = "The cluster name") final String name,
         @CliOption(key = { "nodeGroup" }, mandatory = true, help = "The node group name") final String nodeGroup,
         @CliOption(key = { "instanceNum" }, mandatory = true, help = "The resized number of instances. It should be larger that existing one") final int instanceNum) {

      if (instanceNum > 1) {
         try {
            restClient.resize(name, nodeGroup, instanceNum);
            CommandsUtils.printCmdSuccess(Constants.OUTPUT_OBJECT_CLUSTER,
                  name, Constants.OUTPUT_OP_RESULT_RESIZE);
         } catch (CliRestException e) {
            CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER,
                  name, Constants.OUTPUT_OP_RESIZE,
                  Constants.OUTPUT_OP_RESULT_FAIL, e.getMessage());
         }
      } else {
         CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_RESIZE, Constants.OUTPUT_OP_RESULT_FAIL,
               Constants.INVALID_VALUE + " instanceNum=" + instanceNum);
      }
   }

   private void resumeCreateCluster(final String name) {
      Map<String, String> queryStrings = new HashMap<String, String>();
      queryStrings.put(Constants.QUERY_ACTION_KEY,
            Constants.QUERY_ACTION_RESUME);

      try {
         restClient.actionOps(name, queryStrings);
         CommandsUtils.printCmdSuccess(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_RESULT_RESUME);
      } catch (CliRestException e) {
         CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER, name,
               Constants.OUTPUT_OP_RESUME, Constants.OUTPUT_OP_RESULT_FAIL,
               e.getMessage());
      }
   }

   private List<String> getNetworkNames() {

      List<String> networkNames = new ArrayList<String>(0);

      NetworkRead[] networks = networkRestClient.getAll(false);

      if (networks != null) {
         for (NetworkRead network : networks)
            networkNames.add(network.getName());
      }
      return networkNames;
   }

   private List<String> getDistroNames() {

      List<String> distroNames = new ArrayList<String>(0);

      DistroRead[] distros = distroRestClient.getAll();

      if (distros != null) {
         for (DistroRead distro : distros)
            distroNames.add(distro.getName());
      }
      return distroNames;
   }

   private boolean validName(String inputName, List<String> validNames) {
      for (String name: validNames) {
         if (name.equals(inputName)) {
            return true;
         }
      }
      return false;
   }

   private void prettyOutputClusterInfo(ClusterRead cluster, boolean detail) {
      //cluster Name
      System.out.printf("name: %s, distro: %s, status: %s", cluster.getName(),
            cluster.getDistro(), cluster.getStatus());
      System.out.println();
      LinkedHashMap<String, List<String>> ngColumnNamesWithGetMethodNames =
            new LinkedHashMap<String, List<String>>();
      List<NodeGroupRead> nodegroups = cluster.getNodeGroups();
      if (nodegroups != null) {
         ngColumnNamesWithGetMethodNames.put(
               Constants.FORMAT_TABLE_COLUMN_NAME, Arrays.asList("getName"));
         ngColumnNamesWithGetMethodNames.put(
               Constants.FORMAT_TABLE_COLUMN_ROLES, Arrays.asList("getRoles"));
         ngColumnNamesWithGetMethodNames.put(
               Constants.FORMAT_TABLE_COLUMN_INSTANCE,
               Arrays.asList("getInstanceNum"));
         ngColumnNamesWithGetMethodNames.put(Constants.FORMAT_TABLE_COLUMN_CPU,
               Arrays.asList("getCpuNum"));
         ngColumnNamesWithGetMethodNames.put(Constants.FORMAT_TABLE_COLUMN_MEM,
               Arrays.asList("getMemCapacityMB"));
         ngColumnNamesWithGetMethodNames.put(
               Constants.FORMAT_TABLE_COLUMN_TYPE,
               Arrays.asList("getStorage", "getType"));
         ngColumnNamesWithGetMethodNames.put(
               Constants.FORMAT_TABLE_COLUMN_SIZE,
               Arrays.asList("getStorage", "getSizeGB"));

         try {
            if (detail) {
               LinkedHashMap<String, List<String>> nColumnNamesWithGetMethodNames =
                     new LinkedHashMap<String, List<String>>();
               nColumnNamesWithGetMethodNames.put(
                     Constants.FORMAT_TABLE_COLUMN_NAME,
                     Arrays.asList("getName"));
               nColumnNamesWithGetMethodNames.put(
                     Constants.FORMAT_TABLE_COLUMN_HOST,
                     Arrays.asList("getHostName"));
               nColumnNamesWithGetMethodNames.put(
                     Constants.FORMAT_TABLE_COLUMN_IP, Arrays.asList("getIp"));
               nColumnNamesWithGetMethodNames.put(
                     Constants.FORMAT_TABLE_COLUMN_STATUS,
                     Arrays.asList("getStatus"));

               for (NodeGroupRead nodegroup : nodegroups) {
                  CommandsUtils.printInTableFormat(
                        ngColumnNamesWithGetMethodNames,
                        new NodeGroupRead[] { nodegroup },
                        Constants.OUTPUT_INDENT);
                  List<NodeRead> nodes = nodegroup.getInstances();
                  if (nodes != null) {
                     System.out.println();
                     CommandsUtils.printInTableFormat(
                           nColumnNamesWithGetMethodNames, nodes.toArray(),
                           new StringBuilder().append(Constants.OUTPUT_INDENT)
                                 .append(Constants.OUTPUT_INDENT).toString());
                  }
                  System.out.println();
               }
            } else
               CommandsUtils.printInTableFormat(
                     ngColumnNamesWithGetMethodNames, nodegroups.toArray(),
                     Constants.OUTPUT_INDENT);
         } catch (Exception e) {
            CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER,
                  cluster.getName(), Constants.OUTPUT_OP_LIST,
                  Constants.OUTPUT_OP_RESULT_FAIL, e.getMessage());
         }
      }
   }

   private void prettyOutputClustersInfo(ClusterRead[] clusters, boolean detail) {
      for (ClusterRead cluster : clusters) {
         prettyOutputClusterInfo(cluster, detail);
         System.out.println();
      }
   }

   /**
    * Validate nodeGroupCreates member formats and values in the ClusterCreate.
    */
   private boolean validateClusterCreate(ClusterCreate clusterCreate) {
      // validation status 
      boolean validated = true;
      // show warning message
      boolean warning = false;
      //role count
      int masterCount = 0, workerCount = 0, clientCount = 0;
      //Find NodeGroupCreate array from current ClusterCreate instance.
      NodeGroupCreate[] nodeGroupCreates = clusterCreate.getNodeGroupCreates();
      if (nodeGroupCreates == null || nodeGroupCreates.length == 0) {
         CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER,
               clusterCreate.getName(), Constants.OUTPUT_OP_CREATE,
               Constants.OUTPUT_OP_RESULT_FAIL, Constants.MULTI_INPUTS_CHECK);
         return !validated;
      } else {
         //used for collecting failed message.
         List<String> failedMsgList = new LinkedList<String>();
         //find distro roles.
         List<String> distroRoles = findDistroRoles(clusterCreate);
         if (distroRoles == null) {
            CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER,
                  clusterCreate.getName(), Constants.OUTPUT_OP_CREATE,
                  Constants.OUTPUT_OP_RESULT_FAIL,
                  Constants.PARAM_NO_DISTRO_AVAILABLE);
            return !validated;
         }
         if (nodeGroupCreates.length < 2 || nodeGroupCreates.length > 3) {
            warning = true;
         }
         for (NodeGroupCreate nodeGroupCreate : nodeGroupCreates) {
            //check node group's instanceNum
            if (!checkInstanceNum(nodeGroupCreate, failedMsgList)) {
               validated = false;
            }
            //check node group's roles 
            if (!checkNodeGroupRoles(nodeGroupCreate, distroRoles,
                  failedMsgList)) {
               validated = false;
            }
            // get node group role .
            NodeGroupRole role = getNodeGroupRole(nodeGroupCreate);
            switch (role) {
            case MASTER:
               masterCount++;
               if (nodeGroupCreate.getInstanceNum() >= 0
                     && nodeGroupCreate.getInstanceNum() != 1) {
                  validated = false;
                  collectInstanceNumInvalidateMsg(nodeGroupCreate,
                        failedMsgList);
               }
               break;
            case WORKER:
               workerCount++;
               if (nodeGroupCreate.getInstanceNum() == 0) {
                  validated = false;
                  collectInstanceNumInvalidateMsg(nodeGroupCreate,
                        failedMsgList);
               } else if (nodeGroupCreate.isHaFlag()) {
                  warning = true;
               }
               break;
            case CLIENT:
               clientCount++;
               if (nodeGroupCreate.getInstanceNum() == 0
                     || nodeGroupCreate.isHaFlag()) {
                  warning = true;
               }
               break;
            case NONE:
               warning = true;
               break;
            default:
            }
         }
         if (masterCount != 1 || workerCount != 1 || clientCount > 1) {
            warning = true;
         }
         if (!validated) {
            showFailedMsg(clusterCreate.getName(), failedMsgList);
         } else if (warning) {
            // If warning is true,show waring message.
            showWarningMsg();
            // When exist warning message,whether to proceed
            if (!isContinue(clusterCreate)) {
               validated = false;
            }
         }
         return validated;
      }
   }

   private boolean isContinue(ClusterCreate clusterCreate) {
      boolean continueCreate = true;
      boolean continueLoop = true;
      String readMsg = "";
      try {
         ConsoleReader reader = new ConsoleReader();
         int k = 0;
         while (continueLoop) {
            if (k >= 3) {
               continueCreate = false;
               break;
            }
            // Prompt continue infomation
            System.out.println(Constants.PARAM_PROMPT_CONTINUE_MESSAGE);
            // Get user's entering
            readMsg = reader.readLine();
            if (readMsg.trim().equalsIgnoreCase("yes")
                  || readMsg.trim().equalsIgnoreCase("y")) {
               continueLoop = false;
            } else if (readMsg.trim().equalsIgnoreCase("no")
                  || readMsg.trim().equalsIgnoreCase("n")) {
               continueLoop = false;
               continueCreate = false;
            } else {
               k++;
            }
         }
      } catch (Exception e) {
         CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER,
               clusterCreate.getName(), Constants.OUTPUT_OP_CREATE,
               Constants.OUTPUT_OP_RESULT_FAIL, e.getMessage());
         continueCreate = false;
      }
      return continueCreate;
   }

   private NodeGroupRole getNodeGroupRole(NodeGroupCreate nodeGroupCreate) {
      //Find roles list from current  NodeGroupCreate instance.
      List<String> roles = nodeGroupCreate.getRoles();
      for (NodeGroupRole role : NodeGroupRole.values()) {
         if (matchRole(role, roles)) {
            return role;
         }
      }
      return NodeGroupRole.NONE;
   }

   /**
    * Check the roles was introduced, whether matching with system's specialize
    * role.
    */
   private boolean matchRole(NodeGroupRole role, List<String> roles) {
      List<String> matchRoles = new LinkedList<String>();
      switch (role) {
      case MASTER:
         if (roles.size() != 2) {
            return false;
         } else {
            matchRoles.add(Constants.ROLE_HADOOP_NAME_NODE);
            matchRoles.add(Constants.ROLE_HADOOP_JOB_TRACKER);
            matchRoles.removeAll(roles);
            return matchRoles.size() == 0 ? true : false;
         }
      case WORKER:
         if (roles.size() != 2) {
            return false;
         } else {
            matchRoles.add(Constants.ROLE_HADOOP_DATANODE);
            matchRoles.add(Constants.ROLE_HADOOP_TASKTRACKER);
            matchRoles.removeAll(roles);
            return matchRoles.size() == 0 ? true : false;
         }
      case CLIENT:
         if (roles.size() < 1 || roles.size() > 3) {
            return false;
         } else {
            matchRoles.add(Constants.ROLE_HADOOP_CLIENT);
            matchRoles.add(Constants.ROLE_HIVE);
            matchRoles.add(Constants.ROLE_PIG);
            int diffNum = matchRoles.size() - roles.size();
            matchRoles.removeAll(roles);
            return roles.contains(Constants.ROLE_HADOOP_CLIENT)
                  && (diffNum >= 0) && (diffNum == matchRoles.size()) ? true
                  : false;
         }
      }
      return false;
   }

   private void showWarningMsg() {
      System.out.println(Constants.PARAM_CLUSTER_WARNING);
   }

   private boolean checkInstanceNum(NodeGroupCreate nodeGroup,
         List<String> failedMsgList) {
      boolean validated = true;
      if (nodeGroup.getInstanceNum() < 0) {
         validated = false;
         collectInstanceNumInvalidateMsg(nodeGroup, failedMsgList);
      }
      return validated;
   }

   private void collectInstanceNumInvalidateMsg(NodeGroupCreate nodeGroup,
         List<String> failedMsgList) {
      failedMsgList.add(new StringBuilder().append(nodeGroup.getName())
            .append(".").append("instanceNum=")
            .append(nodeGroup.getInstanceNum()).toString());
   }


   private boolean checkNodeGroupRoles(NodeGroupCreate nodeGroup,
         List<String> distroRoles, List<String> failedMsgList) {
      List<String> roles = nodeGroup.getRoles();
      boolean validated = true;
      StringBuilder rolesMsg = new StringBuilder();
      for (String role : roles) {
         if (!distroRoles.contains(role)) {
            validated = false;
            rolesMsg.append(",").append(role);
         }
      }
      if (!validated) {
         rolesMsg.replace(0, 1, "");
         failedMsgList.add(new StringBuilder().append(nodeGroup.getName())
               .append(".").append("roles=").append("\"")
               .append(rolesMsg.toString()).append("\"").toString());
      }
      return validated;
   }

   private List<String> findDistroRoles(ClusterCreate clusterCreate) {
      DistroRead distroRead = null;
      distroRead =
            distroRestClient
                  .get(clusterCreate.getDistro() != null ? clusterCreate
                        .getDistro() : Constants.DEFAULT_DISTRO);
      if (distroRead != null) {
         return distroRead.getRoles();
      } else {
         return null;
      }
   }

   private void showFailedMsg(String name, List<String> failedMsgList) {
      //cluster creation failed message.
      StringBuilder failedMsg = new StringBuilder();
      failedMsg.append(Constants.INVALID_VALUE);
      if (failedMsgList.size() > 1) {
         failedMsg.append("s");
      }
      failedMsg.append(" ");
      StringBuilder tmpMsg = new StringBuilder();
      for (String msg : failedMsgList) {
         tmpMsg.append(",").append(msg);
      }
      tmpMsg.replace(0, 1, "");
      failedMsg.append(tmpMsg);
      CommandsUtils.printCmdFailure(Constants.OUTPUT_OBJECT_CLUSTER, name,
            Constants.OUTPUT_OP_CREATE, Constants.OUTPUT_OP_RESULT_FAIL,
            failedMsg.toString());
   }
}