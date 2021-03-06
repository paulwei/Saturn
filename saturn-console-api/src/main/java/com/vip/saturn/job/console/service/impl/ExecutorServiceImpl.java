/**
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.vip.saturn.job.console.service.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.vip.saturn.job.console.domain.*;
import com.vip.saturn.job.console.exception.SaturnJobConsoleException;
import com.vip.saturn.job.console.repository.zookeeper.CuratorRepository;
import com.vip.saturn.job.console.repository.zookeeper.CuratorRepository.CuratorFrameworkOp;
import com.vip.saturn.job.console.service.ExecutorService;
import com.vip.saturn.job.console.service.JobService;
import com.vip.saturn.job.console.service.RegistryCenterService;
import com.vip.saturn.job.console.service.SystemConfigService;
import com.vip.saturn.job.console.service.helper.SystemConfigProperties;
import com.vip.saturn.job.console.utils.ExecutorNodePath;
import com.vip.saturn.job.console.utils.JobNodePath;
import com.vip.saturn.job.console.utils.SaturnConsoleUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Default implementation of ExecutorService.
 */
public class ExecutorServiceImpl implements ExecutorService {

	private static final int DEFAULT_MAX_SECONDS_FORCE_KILL_EXECUTOR = 300;
	private static final int SMALLEST_VERSION_SUPPORTED_DUMP = 3;
	private static final Set<String> SUPPORT_DUMP_VERSION_WHITE_LIST = Sets.newHashSet("saturn-dev", "master-SNAPSHOT");

	@Resource
	private CuratorRepository curatorRepository;

	@Resource
	private JobService jobService;

	@Resource
	private RegistryCenterService registryCenterService;

	@Resource
	private SystemConfigService systemConfigService;

	@Override
	public List<ServerBriefInfo> getExecutors(String namespace) throws SaturnJobConsoleException {
		return getExecutors(namespace, null);
	}

	@Override
	public List<ServerBriefInfo> getExecutors(String namespace, ServerStatus expectedServerStatus)
			throws SaturnJobConsoleException {
		CuratorRepository.CuratorFrameworkOp curatorFrameworkOp = getCuratorFrameworkOp(namespace);

		List<String> executors = curatorFrameworkOp.getChildren(ExecutorNodePath.getExecutorNodePath());
		if (executors == null || executors.isEmpty()) {
			return Lists.newArrayList();
		}

		List<ServerBriefInfo> executorInfoList = Lists.newArrayList();
		for (String executor : executors) {
			ServerBriefInfo executorInfo = getServerBriefInfo(executor, curatorFrameworkOp);
			if (expectedServerStatus == null || executorInfo.getStatus() == expectedServerStatus) {
				executorInfoList.add(executorInfo);
			}
		}

		return executorInfoList;
	}

	@Override
	public ServerBriefInfo getExecutor(String namespace, String executorName) throws SaturnJobConsoleException {
		CuratorRepository.CuratorFrameworkOp curatorFrameworkOp = getCuratorFrameworkOp(namespace);

		if (!curatorFrameworkOp.checkExists(ExecutorNodePath.getExecutorNodePath(executorName))) {
			return null;
		}

		return getServerBriefInfo(executorName, curatorFrameworkOp);
	}

	private ServerBriefInfo getServerBriefInfo(String executorName, CuratorFrameworkOp curatorFrameworkOp) {
		ServerBriefInfo executorInfo = new ServerBriefInfo(executorName);
		String ip = curatorFrameworkOp.getData(ExecutorNodePath.getExecutorIpNodePath(executorName));
		executorInfo.setServerIp(ip);
		if (StringUtils.isNotBlank(ip)) {
			executorInfo.setStatus(ServerStatus.ONLINE);
		} else {
			executorInfo.setStatus(ServerStatus.OFFLINE);
		}

		String restartNodePath = ExecutorNodePath.getExecutorRestartNodePath(executorName);
		long restartTriggerTime = curatorFrameworkOp.getCtime(restartNodePath);
		long now = System.currentTimeMillis();

		long maxRestartInv = systemConfigService.getIntegerValue(SystemConfigProperties.MAX_SECONDS_FORCE_KILL_EXECUTOR,
				DEFAULT_MAX_SECONDS_FORCE_KILL_EXECUTOR) * 1000L;

		// ??????restart???????????????restart?????????????????????<300s???executor???????????????RESTARTING???
		if (0 != restartTriggerTime && now - restartTriggerTime < maxRestartInv) {
			executorInfo.setRestarting(true);
		} else {
			executorInfo.setRestarting(false);
		}

		// ?????????????????????
		executorInfo.setNoTraffic(
				curatorFrameworkOp.checkExists(ExecutorNodePath.getExecutorNoTrafficNodePath(executorName)));
		// lastBeginTime
		String lastBeginTime = curatorFrameworkOp
				.getData(ExecutorNodePath.getExecutorNodePath(executorInfo.getExecutorName(), "lastBeginTime"));
		executorInfo.setLastBeginTime(SaturnConsoleUtils.parseMillisecond2DisplayTime(lastBeginTime));
		// version
		executorInfo.setVersion(curatorFrameworkOp.getData(ExecutorNodePath.getExecutorVersionNodePath(executorName)));

		String task = curatorFrameworkOp.getData(ExecutorNodePath.getExecutorTaskNodePath(executorName));
		if (StringUtils.isNotBlank(task)) {
			// ?????????
			executorInfo.setGroupName(task);
			// ????????????
			executorInfo.setContainer(true);
		}
		return executorInfo;
	}

	@Override
	public ServerAllocationInfo getExecutorAllocation(String namespace, String executorName)
			throws SaturnJobConsoleException {
		CuratorRepository.CuratorFrameworkOp curatorFrameworkOp = getCuratorFrameworkOp(namespace);

		List<JobConfig> unSystemJobs = jobService.getUnSystemJobs(namespace);

		ServerAllocationInfo serverAllocationInfo = new ServerAllocationInfo(executorName);

		for (JobConfig jobConfig : unSystemJobs) {
			String jobName = jobConfig.getJobName();
			String serverNodePath = JobNodePath.getServerNodePath(jobName);
			if (!curatorFrameworkOp.checkExists(serverNodePath)) {
				continue;
			}

			String sharding = curatorFrameworkOp
					.getData(JobNodePath.getServerNodePath(jobName, executorName, "sharding"));
			if (StringUtils.isNotBlank(sharding)) {
				// ???????????????STOPPED?????????????????????????????????????????????
				if (JobStatus.STOPPED.equals(jobService.getJobStatus(namespace, jobName))) {
					continue;
				}
				// concat executorSharding
				serverAllocationInfo.getAllocationMap().put(jobName, sharding);
				// calculate totalLoad
				String loadLevelNode = curatorFrameworkOp.getData(JobNodePath.getConfigNodePath(jobName, "loadLevel"));
				Integer loadLevel = 1;
				if (StringUtils.isNotBlank(loadLevelNode)) {
					loadLevel = Integer.valueOf(loadLevelNode);
				}

				int shardingItemNum = sharding.split(",").length;
				int curJobLoad = shardingItemNum * loadLevel;
				int totalLoad = serverAllocationInfo.getTotalLoadLevel();
				serverAllocationInfo.setTotalLoadLevel(totalLoad + curJobLoad);
			}
		}

		return serverAllocationInfo;
	}

	@Override
	public ServerRunningInfo getExecutorRunningInfo(String namespace, String executorName)
			throws SaturnJobConsoleException {
		CuratorRepository.CuratorFrameworkOp curatorFrameworkOp = getCuratorFrameworkOp(namespace);
		List<JobConfig> unSystemJobs = jobService.getUnSystemJobs(namespace);
		ServerRunningInfo serverRunningInfo = new ServerRunningInfo(executorName);

		for (JobConfig jobConfig : unSystemJobs) {
			boolean needToCheckFailover = needToCheckFailover(jobConfig);

			String jobName = jobConfig.getJobName();
			String serverNodePath = JobNodePath.getServerNodePath(jobName);
			if (!curatorFrameworkOp.checkExists(serverNodePath)) {
				continue;
			}

			String sharding = curatorFrameworkOp
					.getData(JobNodePath.getServerNodePath(jobName, executorName, "sharding"));
			Set<String> shardingItems = getShardingItems(sharding);

			if (needToCheckFailover) {
				obtainServerRunningInfoWhileNeedToCheckFailover(executorName, curatorFrameworkOp, jobName,
						shardingItems, serverRunningInfo);
			} else if (!shardingItems.isEmpty()) {
				obtainServerRunningInfoWhileNoNeedToCheckFailover(curatorFrameworkOp, jobConfig, shardingItems,
						serverRunningInfo);
			}

		}

		return serverRunningInfo;
	}

	private void obtainServerRunningInfoWhileNoNeedToCheckFailover(CuratorFrameworkOp curatorFrameworkOp,
			JobConfig jobConfig, Set<String> shardingItems, ServerRunningInfo serverRunningInfo) {
		String jobName = jobConfig.getJobName();
		// ?????????????????????????????????????????????????????????????????????????????????????????????
		if (!jobConfig.getEnabledReport()) {
			if (jobConfig.getEnabled() != null && jobConfig.getEnabled()) {
				serverRunningInfo.getPotentialRunningJobItems().put(jobName, StringUtils.join(shardingItems, ','));
			}
			return;
		}

		List<String> runningItems = Lists.newArrayList();
		for (String item : shardingItems) {
			if (curatorFrameworkOp
					.checkExists(JobNodePath.getExecutionNodePath(jobConfig.getJobName(), item, "running"))) {
				runningItems.add(item);
			}
		}

		if (!runningItems.isEmpty()) {
			serverRunningInfo.getRunningJobItems().put(jobConfig.getJobName(), StringUtils.join(runningItems, ','));
		}
	}

	/**
	 * ??????????????????????????????????????????????????????????????????failover;
	 * ????????????????????????????????????????????????failover??????????????????????????????running??????????????????running??????????????????????????????????????????failover?????????????????????runningInfo????????????continue???
	 */
	private void obtainServerRunningInfoWhileNeedToCheckFailover(String executorName,
			CuratorFrameworkOp curatorFrameworkOp, String jobName, Set<String> shardingItems,
			ServerRunningInfo serverRunningInfo) {
		List<String> executionItems = curatorFrameworkOp.getChildren(JobNodePath.getExecutionNodePath(jobName));
		if (CollectionUtils.isEmpty(executionItems)) {
			return;
		}

		List<String> runningItems = Lists.newArrayList();
		for (String item : executionItems) {
			boolean isItemRunning = curatorFrameworkOp
					.checkExists(JobNodePath.getExecutionNodePath(jobName, item, "running"));
			if (!isItemRunning) {
				continue;
			}
			// ???????????????????????????
			if (shardingItems != null && shardingItems.contains(item)) {
				runningItems.add(item);
				continue;
			}
			// ???????????????????????????????????????????????????failover
			String failoverValue = curatorFrameworkOp
					.getData(JobNodePath.getExecutionNodePath(jobName, item, "failover"));
			if (StringUtils.isNotBlank(failoverValue) && failoverValue.equals(executorName)) {
				runningItems.add(item);
			}
		}

		if (!runningItems.isEmpty()) {
			serverRunningInfo.getRunningJobItems().put(jobName, StringUtils.join(runningItems, ','));
		}
	}

	private Set<String> getShardingItems(String sharding) {
		if (StringUtils.isBlank(sharding)) {
			return Sets.newHashSet();
		}

		String items[] = sharding.split(",");
		Set<String> result = Sets.newTreeSet();
		for (String item : items) {
			if (StringUtils.isBlank(item)) {
				continue;
			}
			result.add(item.trim());
		}
		return result;
	}

	private boolean needToCheckFailover(JobConfig jobConfig) {
		if (!jobConfig.getFailover()) {
			return false;
		}
		//?????????????????????????????????
		if (JobType.isMsg(JobType.getJobType(jobConfig.getJobType()))) {
			return false;
		}
		if (jobConfig.getLocalMode()) {
			return false;
		}
		if (!jobConfig.getEnabledReport()) {
			return false;
		}

		return true;
	}

	@Override
	public void extractTraffic(String namespace, String executorName) throws SaturnJobConsoleException {
		CuratorRepository.CuratorFrameworkOp curatorFrameworkOp = getCuratorFrameworkOp(namespace);
		validateIfExecutorNameExisted(executorName, curatorFrameworkOp);
		curatorFrameworkOp.create(ExecutorNodePath.getExecutorNoTrafficNodePath(executorName));
	}

	@Override
	public void recoverTraffic(String namespace, String executorName) throws SaturnJobConsoleException {
		CuratorRepository.CuratorFrameworkOp curatorFrameworkOp = getCuratorFrameworkOp(namespace);
		validateIfExecutorNameExisted(executorName, curatorFrameworkOp);
		curatorFrameworkOp.deleteRecursive(ExecutorNodePath.getExecutorNoTrafficNodePath(executorName));
	}

	@Override
	public void removeExecutor(String namespace, String executorName) throws SaturnJobConsoleException {
		CuratorRepository.CuratorFrameworkOp curatorFrameworkOp = getCuratorFrameworkOp(namespace);
		curatorFrameworkOp.deleteRecursive(ExecutorNodePath.getExecutorNodePath(executorName));
		List<String> jobNames = jobService.getAllJobNamesFromZK(namespace);

		if (CollectionUtils.isEmpty(jobNames)) {
			return;
		}
		for (String jobName : jobNames) {
			String executorNode = JobNodePath.getServerNodePath(jobName, executorName);
			curatorFrameworkOp.deleteRecursive(executorNode);
		}
	}

	@Override
	public void shardAll(String namespace) throws SaturnJobConsoleException {
		CuratorRepository.CuratorFrameworkOp curatorFrameworkOp = getCuratorFrameworkOp(namespace);
		String shardAllAtOnceNodePath = ExecutorNodePath.getExecutorShardingNodePath("shardAllAtOnce");
		curatorFrameworkOp.deleteRecursive(shardAllAtOnceNodePath);
		curatorFrameworkOp.create(shardAllAtOnceNodePath);
	}

	@Override
	public void dump(String namespace, String executorName) throws SaturnJobConsoleException {
		CuratorRepository.CuratorFrameworkOp curatorFrameworkOp = getCuratorFrameworkOp(namespace);
		String version = curatorFrameworkOp.getData(ExecutorNodePath.getExecutorVersionNodePath(executorName));
		if (!isVersionSupportedDump(version)) {
			throw new SaturnJobConsoleException(SaturnJobConsoleException.ERROR_CODE_BAD_REQUEST,
					"Saturn executor????????????3.0.0??????????????????dump");
		}
		String dumpNodePath = ExecutorNodePath.getExecutorDumpNodePath(executorName);
		curatorFrameworkOp.delete(dumpNodePath);
		curatorFrameworkOp.create(dumpNodePath);
	}

	private boolean isVersionSupportedDump(String version) {
		if (SUPPORT_DUMP_VERSION_WHITE_LIST.contains(version)) {
			return true;
		}

		String[] items = version.split("\\.");
		if (items.length < 3) {
			return false;
		}

		int majorVersion = Integer.parseInt(items[0]);
		return majorVersion >= SMALLEST_VERSION_SUPPORTED_DUMP;
	}

	@Override
	public File dumpAsFile(String namespace, String executorName) throws SaturnJobConsoleException {
		throw new UnsupportedOperationException("this method is not supported yet");
	}

	@Override
	public void restart(String namespace, String executorName) throws SaturnJobConsoleException {
		CuratorRepository.CuratorFrameworkOp curatorFrameworkOp = getCuratorFrameworkOp(namespace);
		String restartNodePath = ExecutorNodePath.getExecutorRestartNodePath(executorName);
		curatorFrameworkOp.delete(restartNodePath);
		curatorFrameworkOp.create(restartNodePath);
	}

	private void validateIfExecutorNameExisted(String executorName,
			CuratorRepository.CuratorFrameworkOp curatorFrameworkOp) throws SaturnJobConsoleException {
		if (!curatorFrameworkOp.checkExists(ExecutorNodePath.getExecutorNodePath(executorName))) {
			throw new SaturnJobConsoleException("The executorName(" + executorName + ") is not existed.");
		}
	}

	protected CuratorFrameworkOp getCuratorFrameworkOp(String namespace) throws SaturnJobConsoleException {
		return registryCenterService.getCuratorFrameworkOp(namespace);
	}
}
