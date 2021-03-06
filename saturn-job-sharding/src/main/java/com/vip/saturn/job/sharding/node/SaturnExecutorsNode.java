/**
 * Copyright 2016 vip.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 * </p>
 **/

package com.vip.saturn.job.sharding.node;

/**
 * @author xiaopeng.he
 */
public class SaturnExecutorsNode {

	public static final String JOBS_NODE = "$Jobs";
	private static final String SATURN_EXECUTORS_NODE = "$SaturnExecutors";
	private static final String EXECUTORS = "executors";
	private static final String HOST = "host";
	private static final String LEADER = "leader";
	private static final String LATCH = "latch";
	private static final String SHARDING = "sharding";
	private static final String CONTENT = "content";
	private static final String IP = "ip";
	private static final String NO_TRAFFIC = "noTraffic";
	private static final String CLEAN = "clean";
	private static final String TASK = "task";
	private static final String DCOS_NODE = "$DCOS";
	private static final String TASKS = "tasks";
	public static final String SHARDING_COUNT_PATH = "/" + SATURN_EXECUTORS_NODE + "/" + SHARDING + "/" + "count";
	public static final String LEADER_HOSTNODE_PATH = "/" + SATURN_EXECUTORS_NODE + "/" + LEADER + "/" + HOST;
	public static final String LEADERNODE_PATH = "/" + SATURN_EXECUTORS_NODE + "/" + LEADER;
	public static final String EXECUTORSNODE_PATH = "/" + SATURN_EXECUTORS_NODE + "/" + EXECUTORS;
	public static final String SHARDINGNODE_PATH = "/" + SATURN_EXECUTORS_NODE + "/" + SHARDING;
	public static final String LEADER_LATCHNODE_PATH = "/" + SATURN_EXECUTORS_NODE + "/" + LEADER + "/" + LATCH;
	public static final String SHARDING_CONTENTNODE_PATH = "/" + SATURN_EXECUTORS_NODE + "/" + SHARDING + "/" + CONTENT;
	public static final String EXECUTOR_IPNODE_PATH_REGEX =
			"/\\" + SATURN_EXECUTORS_NODE + "/" + EXECUTORS + "/" + "[^/]*"
					+ "/" + IP;
	public static final String EXECUTOR_NO_TRAFFIC_NODE_PATH_REGEX =
			"/\\" + SATURN_EXECUTORS_NODE + "/" + EXECUTORS + "/" + "[^/]*"
					+ "/" + NO_TRAFFIC;
	public static final String CONFIG_VERSION_PATH = "/" + SATURN_EXECUTORS_NODE + "/config/version";

	public static final String JOBSNODE_PATH = "/" + JOBS_NODE;
	public static final String SATURNEXECUTORS_PATH = "/" + SATURN_EXECUTORS_NODE;

	/**
	 * ??????$SaturnExecutors??????????????????
	 */
	public static String getSaturnExecutorsNodeName() {
		return SATURN_EXECUTORS_NODE;
	}

	/**
	 * ??????$SaturnExecutors/executors??????????????????
	 */
	public static String getExecutorsNodePath() {
		return "/" + SATURN_EXECUTORS_NODE + "/" + EXECUTORS;
	}

	/**
	 * ??????ip????????????
	 */
	public static String getIpNodeName() {
		return IP;
	}

	/**
	 * ??????noTraffic????????????
	 */
	public static String getNoTrafficNodeName() {
		return NO_TRAFFIC;
	}

	/**
	 * ??????$SaturnExecutors/executors/xx??????????????????
	 */
	public static String getExecutorNodePath(String executor) {
		return "/" + SATURN_EXECUTORS_NODE + "/" + EXECUTORS + "/" + executor;
	}

	/**
	 * ??????$SaturnExecutors/executors/xx/ip??????????????????
	 */
	public static String getExecutorIpNodePath(String executor) {
		return "/" + SATURN_EXECUTORS_NODE + "/" + EXECUTORS + "/" + executor + "/" + IP;
	}

	/**
	 * ??????$SaturnExecutors/executors/xx/noTraffic??????????????????
	 *
	 * @return true ?????????????????????false???otherwise???
	 */
	public static String getExecutorNoTrafficNodePath(String executor) {
		return "/" + SATURN_EXECUTORS_NODE + "/" + EXECUTORS + "/" + executor + "/" + NO_TRAFFIC;
	}

	/**
	 * ??????$SaturnExecutors/executors/xx/clean??????????????????
	 */
	public static String getExecutorCleanNodePath(String executor) {
		return "/" + SATURN_EXECUTORS_NODE + "/" + EXECUTORS + "/" + executor + "/" + CLEAN;
	}

	/**
	 * ??????$SaturnExecutors/executors/xx/task??????????????????
	 */
	public static String getExecutorTaskNodePath(String executor) {
		return "/" + SATURN_EXECUTORS_NODE + "/" + EXECUTORS + "/" + executor + "/" + TASK;
	}

	/**
	 * ?????????????????????executorName
	 */
	public static String getExecutorNameByIpPath(String path) {
		return getExecutorNameByPath(path, getIpNodeName());
	}

	/**
	 * ?????????????????????executorName
	 */
	public static String getExecutorNameByNoTrafficPath(String path) {
		return getExecutorNameByPath(path, getNoTrafficNodeName());
	}

	private static String getExecutorNameByPath(String path, String nodeName) {
		int lastIndexOf = path.lastIndexOf("/" + nodeName);
		String substring = path.substring(0, lastIndexOf);
		int lastIndexOf2 = substring.lastIndexOf('/');
		return substring.substring(lastIndexOf2 + 1);
	}

	/**
	 * ??????$SaturnExecutors/sharding??????????????????
	 */
	public static String getExecutorShardingNodePath(String nodeName) {
		return "/" + SATURN_EXECUTORS_NODE + "/" + SHARDING + "/" + nodeName;
	}

	/**
	 * ??????$SaturnExecutors/sharding/content??????????????????
	 */
	public static String getShardingContentElementNodePath(String element) {
		return "/" + SATURN_EXECUTORS_NODE + "/" + SHARDING + "/" + CONTENT + "/" + element;
	}

	/**
	 * ??????????????????????????????
	 */
	public static String getJobNodePath(String jobName) {
		return String.format("/%s/%s", JOBS_NODE, jobName);
	}

	/**
	 * ??????$Jobs/xx/config/shardingTotalCount??????????????????
	 */
	public static String getJobConfigShardingTotalCountNodePath(String jobName) {
		return String.format("/%s/%s/%s/%s", JOBS_NODE, jobName, "config", "shardingTotalCount");
	}

	/**
	 * ??????$Jobs/xx/config/loadLevel??????????????????
	 */
	public static String getJobConfigLoadLevelNodePath(String jobName) {
		return String.format("/%s/%s/%s/%s", JOBS_NODE, jobName, "config", "loadLevel");
	}

	/**
	 * ??????$Jobs/xx/config/preferList??????????????????
	 */
	public static String getJobConfigPreferListNodePath(String jobName) {
		return String.format("/%s/%s/%s/%s", JOBS_NODE, jobName, "config", "preferList");
	}

	/**
	 * ??????$Jobs/xx/config/enabled??????????????????
	 */
	public static String getJobConfigEnableNodePath(String jobName) {
		return String.format("/%s/%s/%s/%s", JOBS_NODE, jobName, "config", "enabled");
	}

	/**
	 * ??????$Jobs/xx/config/localMode??????????????????
	 */
	public static String getJobConfigLocalModeNodePath(String jobName) {
		return String.format("/%s/%s/%s/%s", JOBS_NODE, jobName, "config", "localMode");
	}

	/**
	 * ??????$Jobs/xx/config/useSerial??????????????????
	 */
	public static String getJobConfigUseSerialNodePath(String jobName) {
		return String.format("/%s/%s/%s/%s", JOBS_NODE, jobName, "config", "useSerial");
	}

	/**
	 * ??????$Jobs/xx/config/useDispreferList??????????????????
	 */
	public static String getJobConfigUseDispreferListNodePath(String jobName) {
		return String.format("/%s/%s/%s/%s", JOBS_NODE, jobName, "config", "useDispreferList");
	}

	/**
	 * ??????$Jobs/xx/config/forceShard??????????????????
	 */
	public static String getJobConfigForceShardNodePath(String jobName) {
		return String.format("/%s/%s/%s/%s", JOBS_NODE, jobName, "config", "forceShard");
	}

	/**
	 * ??????$Jobs/xx/leader/sharding/necessary????????????
	 */
	public static String getJobLeaderShardingNecessaryNodePath(String jobName) {
		return String.format("/%s/%s/%s/%s/%s", JOBS_NODE, jobName, "leader", "sharding", "necessary");
	}

	/**
	 * ??????$Jobs/xx/leader/sharding????????????
	 */
	public static String getJobLeaderShardingNodePath(String jobName) {
		return String.format("/%s/%s/%s/%s", JOBS_NODE, jobName, "leader", "sharding");
	}

	/**
	 * ??????$Jobs/xx/execution????????????
	 */
	public static String getJobExecutionNodePath(final String jobName) {
		return String.format("/%s/%s/execution", JOBS_NODE, jobName);
	}

	/**
	 * ??????$Jobs/xx/servers????????????
	 */
	public static String getJobServersNodePath(String jobName) {
		return String.format("/%s/%s/servers", JOBS_NODE, jobName);
	}

	/**
	 * ??????$Jobs/xx/servers/yy????????????
	 */
	public static String getJobServersExecutorNodePath(String jobName, String executorName) {
		return String.format("/%s/%s/servers/%s", JOBS_NODE, jobName, executorName);
	}

	/**
	 * Get the $/Jobs/jobName/servers/executorName/status node path
	 */
	public static String getJobServersExecutorStatusNodePath(String jobName, String executorName) {
		return String.format("/%s/%s/servers/%s/%s", JOBS_NODE, jobName, executorName, "status");
	}

	public static String getJobServersExecutorStatusNodePathRegex(String jobName) {
		return "/\\" + JOBS_NODE + "/" + jobName + "/" + "servers" + "/" + "[^/]*" + "/" + "status";
	}

	public static String getJobServersExecutorNameByStatusPath(String path) {
		int beginIndexOf = path.lastIndexOf("/servers/") + 9;
		int lastIndexOf = path.lastIndexOf("/status");
		return path.substring(beginIndexOf, lastIndexOf);
	}

	/**
	 * ??????$DCOS/tasks/xxx????????????
	 */
	public static String getDcosTaskNodePath(String task) {
		return String.format("/%s/%s/%s", DCOS_NODE, TASKS, task);
	}

}
