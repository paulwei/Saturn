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

package com.vip.saturn.job.console.service.impl.statistics.analyzer;

import com.vip.saturn.job.console.domain.AbnormalJob;
import com.vip.saturn.job.console.domain.AbnormalShardingState;
import com.vip.saturn.job.console.domain.JobType;
import com.vip.saturn.job.console.domain.RegistryCenterConfiguration;
import com.vip.saturn.job.console.repository.zookeeper.CuratorRepository;
import com.vip.saturn.job.console.service.JobService;
import com.vip.saturn.job.console.service.helper.DashboardConstants;
import com.vip.saturn.job.console.service.helper.DashboardServiceHelper;
import com.vip.saturn.job.console.utils.CronExpression;
import com.vip.saturn.job.console.utils.JobNodePath;
import com.vip.saturn.job.console.utils.SaturnConstants;
import com.vip.saturn.job.integrate.exception.ReportAlarmException;
import com.vip.saturn.job.integrate.service.ReportAlarmService;
import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.vip.saturn.job.console.service.impl.JobServiceImpl.CONFIG_ITEM_RERUN;

public class OutdatedNoRunningJobAnalyzer {

	private static final Logger log = LoggerFactory.getLogger(OutdatedNoRunningJobAnalyzer.class);

	private Map<String/** domainName_jobName_shardingItemStr **/
			, AbnormalShardingState /** abnormal sharding state */> abnormalShardingStateCache = new ConcurrentHashMap<>();

	private ReportAlarmService reportAlarmService;

	private JobService jobService;

	private List<AbnormalJob> outdatedNoRunningJobs = new ArrayList<>();

	private List<AbnormalJob> needReportAlarmJobs = new ArrayList<>();

	private static boolean isCronJob(CuratorRepository.CuratorFrameworkOp curatorFrameworkOp, String jobName) {
		String jobType = curatorFrameworkOp.getData(JobNodePath.getConfigNodePath(jobName, "jobType"));
		return JobType.isCron(JobType.getJobType(jobType));
	}

	private static boolean isEnabledPath(CuratorRepository.CuratorFrameworkOp curatorFrameworkOp,
			AbnormalJob abnormalJob) {
		String enabledPath = JobNodePath.getConfigNodePath(abnormalJob.getJobName(), "enabled");
		return Boolean.parseBoolean(curatorFrameworkOp.getData(enabledPath));
	}

	private static boolean isEnabledReport(CuratorRepository.CuratorFrameworkOp curatorFrameworkOp, String jobName) {
		String enabledReportPath = JobNodePath.getConfigNodePath(jobName, "enabledReport");
		String enabledReportVal = curatorFrameworkOp.getData(enabledReportPath);
		return enabledReportVal == null || "true".equals(enabledReportVal);
	}

	private static long getLastCompleteTime(CuratorRepository.CuratorFrameworkOp curatorFrameworkOp, String jobName,
			String shardingItemStr) {
		String lastCompleteTimePath = JobNodePath.getExecutionNodePath(jobName, shardingItemStr, "lastCompleteTime");
		String data = curatorFrameworkOp.getData(lastCompleteTimePath);
		return StringUtils.isBlank(data) ? 0 : Long.parseLong(data.trim());
	}

	/**
	 * ???????????????????????????????????????????????????
	 * <p>
	 * ??????????????????pausePeriodDate?????????pausePeriodTime?????????????????????????????????????????????????????????????????????????????????????????????
	 *
	 * @return ???????????????????????????????????????????????????
	 */
	private static boolean isInPausePeriod(Date date, String pausePeriodDate, String pausePeriodTime,
			TimeZone timeZone) {
		Calendar calendar = Calendar.getInstance(timeZone);
		calendar.setTime(date);
		int iMon = calendar.get(Calendar.MONTH) + 1; // Calendar.MONTH begin from 0.
		int d = calendar.get(Calendar.DAY_OF_MONTH);
		int h = calendar.get(Calendar.HOUR_OF_DAY);
		int m = calendar.get(Calendar.MINUTE);

		boolean pausePeriodDateIsEmpty = (pausePeriodDate == null || pausePeriodDate.trim().isEmpty());
		boolean dateIn = false;
		if (!pausePeriodDateIsEmpty) {
			dateIn = isDateInPausePeriodDate(iMon, d, pausePeriodDate);
		}

		boolean timeIn = false;
		boolean pausePeriodTimeIsEmpty = (pausePeriodTime == null || pausePeriodTime.trim().isEmpty());
		if (!pausePeriodTimeIsEmpty) {
			timeIn = isTimeInPausePeriodTime(h, m, pausePeriodTime);
		}

		if (pausePeriodDateIsEmpty) {
			if (pausePeriodTimeIsEmpty) {
				return false;
			} else {
				return timeIn;
			}
		} else {
			if (pausePeriodTimeIsEmpty) {
				return dateIn;
			} else {
				return dateIn && timeIn;
			}
		}
	}

	private static boolean isDateInPausePeriodDate(int m, int d, String pausePeriodDate) {
		boolean dateIn = false;

		String[] periodsDate = pausePeriodDate.split(",");
		if (periodsDate == null) {
			return dateIn;
		}
		for (String period : periodsDate) {
			String[] tmp = period.trim().split("-");
			if (tmp == null || tmp.length != 2) {
				dateIn = false;
				break;
			}
			String left = tmp[0].trim();
			String right = tmp[1].trim();
			String[] sMdLeft = left.split("/");
			String[] sMdRight = right.split("/");
			if (sMdLeft != null && sMdLeft.length == 2 && sMdRight != null && sMdRight.length == 2) {
				try {
					int iMLeft = Integer.parseInt(sMdLeft[0]);
					int dLeft = Integer.parseInt(sMdLeft[1]);
					int iMRight = Integer.parseInt(sMdRight[0]);
					int dRight = Integer.parseInt(sMdRight[1]);
					boolean isBiggerThanLeft = m > iMLeft || (m == iMLeft && d >= dLeft);
					boolean isSmallerThanRight = m < iMRight || (m == iMRight && d <= dRight);
					dateIn = isBiggerThanLeft && isSmallerThanRight;
					if (dateIn) {
						break;
					}
				} catch (NumberFormatException e) {
					dateIn = false;
					break;
				}
			} else {
				dateIn = false;
				break;
			}
		}
		return dateIn;
	}

	private static boolean isTimeInPausePeriodTime(int hour, int min, String pausePeriodTime) {
		boolean timeIn = false;
		String[] periodsTime = pausePeriodTime.split(",");
		if (periodsTime == null) {
			return timeIn;
		}
		for (String period : periodsTime) {
			String[] tmp = period.trim().split("-");
			if (tmp == null || tmp.length != 2) {
				timeIn = false;
				break;
			}
			String left = tmp[0].trim();
			String right = tmp[1].trim();
			String[] hmLeft = left.split(":");
			String[] hmRight = right.split(":");
			if (hmLeft != null && hmLeft.length == 2 && hmRight != null && hmRight.length == 2) {
				try {
					int hLeft = Integer.parseInt(hmLeft[0]);
					int mLeft = Integer.parseInt(hmLeft[1]);
					int hRight = Integer.parseInt(hmRight[0]);
					int mRight = Integer.parseInt(hmRight[1]);
					boolean isBiggerThanLeft = hour > hLeft || (hour == hLeft && min >= mLeft);
					boolean isSmallerThanRight = hour < hRight || (hour == hRight && min <= mRight);
					timeIn = isBiggerThanLeft && isSmallerThanRight;
					if (timeIn) {
						break;
					}
				} catch (NumberFormatException e) {
					timeIn = false;
					break;
				}
			} else {
				timeIn = false;
				break;
			}
		}
		return timeIn;
	}

	public void analyze(CuratorRepository.CuratorFrameworkOp curatorFrameworkOp, List<AbnormalJob> oldAbnormalJobs,
			String jobName, String jobDegree, RegistryCenterConfiguration config) {
		AbnormalJob unnormalJob = new AbnormalJob(jobName, config.getNamespace(), config.getNameAndNamespace(),
				config.getDegree());
		unnormalJob.setJobDegree(jobDegree);
		checkOutdatedNoRunningJob(oldAbnormalJobs, curatorFrameworkOp, unnormalJob);
	}

	private synchronized boolean contains(AbnormalJob abnormalJob) {
		return outdatedNoRunningJobs.contains(abnormalJob);
	}

	private synchronized void addAbnormalJob(AbnormalJob abnormalJob) {
		outdatedNoRunningJobs.add(abnormalJob);
	}

	private synchronized void addNeedReportAlarmJob(AbnormalJob abnormalJob) {
		needReportAlarmJobs.add(abnormalJob);
	}

	private void checkOutdatedNoRunningJob(List<AbnormalJob> oldAbnormalJobs,
			CuratorRepository.CuratorFrameworkOp curatorFrameworkOp, AbnormalJob abnormalJob) {
		try {
			if (!isCronJob(curatorFrameworkOp, abnormalJob.getJobName())) {
				return;
			}
			if (!isEnabledPath(curatorFrameworkOp, abnormalJob)) {
				return;
			}
			if (!isEnabledReport(curatorFrameworkOp, abnormalJob.getJobName())) {
				return;
			}
			doCheckAndHandleOutdatedNoRunningJob(oldAbnormalJobs, curatorFrameworkOp, abnormalJob);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	/**
	 * ???????????????????????????
	 */
	private void doCheckAndHandleOutdatedNoRunningJobByShardingItem(List<AbnormalJob> oldAbnormalJobs,
			CuratorRepository.CuratorFrameworkOp curatorFrameworkOp, AbnormalJob abnormalJob, String enabledPath,
			String item) {
		if (contains(abnormalJob)) {
			return;
		}
		String jobName = abnormalJob.getJobName();
		int cversion = getCversion(curatorFrameworkOp, JobNodePath.getExecutionItemNodePath(jobName, item));
		long nextFireTime = checkShardingItemState(curatorFrameworkOp, abnormalJob, enabledPath, item);
		if (nextFireTime != -1 && doubleCheckShardingState(abnormalJob, item, cversion)
				&& !mayBlockWaitingRunningItemEnd(curatorFrameworkOp, abnormalJob, nextFireTime, item)) {
			if (abnormalJob.getCause() == null) {
				abnormalJob.setCause(AbnormalJob.Cause.NOT_RUN.name());
			}
			handleOutdatedNoRunningJob(oldAbnormalJobs, curatorFrameworkOp, abnormalJob, nextFireTime);
		}
	}

	/**
	 * ???????????????????????????????????????running?????????item????????????
	 *
	 * ????????????job?????????item???running????????????????????????????????????item??????????????????
	 * 1.???console????????????????????????????????????????????????item??????running???item
	 * 2.executor???????????????failover???item???????????????????????????item??????????????????
	 * 3.?????????failover???item?????????????????????executor??????running???????????????????????????????????????failover
	 * ???????????????????????????
	 *
	 * ???????????????:
	 * ?????????job?????????running???item???
	 * 1.??????failover??????mtime
	 * 2.?????????????????????????????????
	 * @return ??????false???????????????
	 */
	private boolean mayBlockWaitingRunningItemEnd(CuratorRepository.CuratorFrameworkOp curatorFrameworkOp,
			AbnormalJob abnormalJob, long nextFireTime, String shardingItemStr) {
		List<String> executionItems = curatorFrameworkOp
				.getChildren(JobNodePath.getExecutionNodePath(abnormalJob.getJobName()));

		boolean hasRunningItem = false;
		if (!CollectionUtils.isEmpty(executionItems)) {
			for (String item : executionItems) {
				String runningNodePath = JobNodePath.getRunningNodePath(abnormalJob.getJobName(), item);
				if (curatorFrameworkOp.checkExists(runningNodePath)) {
					hasRunningItem = true;
					break;
				}
			}
		}

		if (!hasRunningItem) {
			return false;
		}

		//??????failover??????mtime??????nextFireTime????????????nextFireTime
		long currentTime = System.currentTimeMillis();
		long failoverMtime;
		String failoverNodePath = JobNodePath.getFailoverNodePath(abnormalJob.getJobName(), shardingItemStr);
		if ((failoverMtime = curatorFrameworkOp.getMtime(failoverNodePath)) <= 0) {
			String leaderFailoverItemPath = JobNodePath
					.getLeaderFailoverItemPath(abnormalJob.getJobName(), shardingItemStr);
			failoverMtime = curatorFrameworkOp.getMtime(leaderFailoverItemPath);
		}

		long nextFireTimeTmp = failoverMtime > nextFireTime ? failoverMtime : nextFireTime;
		return nextFireTimeTmp + DashboardConstants.NOT_RUNNING_WARN_DELAY_MS_WHEN_JOB_RUNNING > currentTime;
	}

	private int getCversion(CuratorRepository.CuratorFrameworkOp curatorFrameworkOp, String path) {
		int cversion = 0;
		Stat stat = curatorFrameworkOp.getStat(path);
		if (stat != null) {
			cversion = stat.getCversion();
		}
		return cversion;
	}

	/**
	 * ??????????????????????????????true???????????????false ALLOW_DELAY_MILLIONSECONDS * 1.5 ??????????????????????????????????????? ????????????????????? 1???????????????+?????????????????????????????????2??????
	 * 2???????????????CVersion?????????????????????????????????????????????????????????????????????????????????
	 */
	private boolean doubleCheckShardingState(AbnormalJob abnormalJob, String shardingItemStr, int zkNodeCVersion) {
		String key = abnormalJob.getDomainName() + "_" + abnormalJob.getJobName() + "_" + shardingItemStr;
		long nowTime = System.currentTimeMillis();

		if (abnormalShardingStateCache.containsKey(key)) {
			AbnormalShardingState abnormalShardingState = abnormalShardingStateCache.get(key);
			if (abnormalShardingState != null
					&& abnormalShardingState.getAlertTime() + DashboardConstants.ALLOW_DELAY_MILLIONSECONDS * 1.5
					> nowTime && abnormalShardingState.getZkNodeCVersion() == zkNodeCVersion) {
				abnormalShardingStateCache.put(key, new AbnormalShardingState(nowTime, zkNodeCVersion));// ????????????
				return true;
			} else {
				abnormalShardingStateCache.put(key, new AbnormalShardingState(nowTime, zkNodeCVersion));// ??????????????????????????????
				return false;
			}
		} else {
			abnormalShardingStateCache.put(key, new AbnormalShardingState(nowTime, zkNodeCVersion));// ??????????????????
			return false;
		}
	}

	private void doCheckAndHandleOutdatedNoRunningJob(List<AbnormalJob> oldAbnormalJobs,
			CuratorRepository.CuratorFrameworkOp curatorFrameworkOp, AbnormalJob abnormalJob) {
		String jobName = abnormalJob.getJobName();
		String enabledPath = JobNodePath.getConfigNodePath(abnormalJob.getJobName(), "enabled");
		List<String> items = curatorFrameworkOp.getChildren(JobNodePath.getExecutionNodePath(abnormalJob.getJobName()));
		if (items != null && !items.isEmpty()) { // ?????????
			int shardingTotalCount = Integer
					.parseInt(curatorFrameworkOp.getData(JobNodePath.getConfigNodePath(jobName, "shardingTotalCount")));
			for (String item : items) {
				int each = Integer.parseInt(item);
				if (each < shardingTotalCount) { // ????????????????????????
					doCheckAndHandleOutdatedNoRunningJobByShardingItem(oldAbnormalJobs, curatorFrameworkOp, abnormalJob,
							enabledPath, item);
				}
			}
		} else { // ??????????????????????????????????????????????????????
			abnormalJob.setCause(AbnormalJob.Cause.NO_SHARDS.name());
			long nextFireTimeAfterThis = curatorFrameworkOp.getMtime(enabledPath);
			Long nextFireTime = getNextFireTimeAfterSpecifiedTimeExcludePausePeriod(nextFireTimeAfterThis, jobName,
					curatorFrameworkOp);
			// ??????????????????????????????????????????+??????, ??????????????????????????????
			if (nextFireTime != null && nextFireTime + DashboardConstants.ALLOW_DELAY_MILLIONSECONDS < System
					.currentTimeMillis()) {
				handleOutdatedNoRunningJob(oldAbnormalJobs, curatorFrameworkOp, abnormalJob, nextFireTime);
			}
		}
	}

	private void handleOutdatedNoRunningJob(List<AbnormalJob> oldAbnormalJobs,
			CuratorRepository.CuratorFrameworkOp curatorFrameworkOp, AbnormalJob abnormalJob, Long nextFireTime) {
		String jobName = abnormalJob.getJobName();
		String timeZone = getTimeZone(jobName, curatorFrameworkOp);
		// ??????????????????
		fillAbnormalJobInfo(curatorFrameworkOp, oldAbnormalJobs, abnormalJob, abnormalJob.getCause(), timeZone,
				nextFireTime);
		// ????????????????????????
		reportAlarmAbnormalJobIfNecessary(curatorFrameworkOp, abnormalJob);
		addAbnormalJob(abnormalJob);
	}

	private void fillAbnormalJobInfo(CuratorRepository.CuratorFrameworkOp curatorFrameworkOp,
			List<AbnormalJob> oldAbnormalJobs, AbnormalJob abnormalJob, String cause, String timeZone,
			long nextFireTime) {
		if (executorNotReady(curatorFrameworkOp, abnormalJob)) {
			cause = AbnormalJob.Cause.EXECUTORS_NOT_READY.name();
		}
		abnormalJob.setCause(cause);
		abnormalJob.setTimeZone(timeZone);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone(timeZone));
		abnormalJob.setNextFireTimeWithTimeZoneFormat(sdf.format(nextFireTime));
		abnormalJob.setNextFireTime(nextFireTime);
		// ????????????????????????????????????????????????
		AbnormalJob oldAbnormalJob = DashboardServiceHelper.findEqualAbnormalJob(abnormalJob, oldAbnormalJobs);
		if (oldAbnormalJob != null) {
			abnormalJob.setRead(oldAbnormalJob.isRead());
			if (oldAbnormalJob.getUuid() != null) {
				abnormalJob.setUuid(oldAbnormalJob.getUuid());
			} else {
				abnormalJob.setUuid(UUID.randomUUID().toString());
			}
			abnormalJob.setHasRerun(oldAbnormalJob.isHasRerun());
		} else {
			abnormalJob.setUuid(UUID.randomUUID().toString());
		}
	}

	private boolean executorNotReady(CuratorRepository.CuratorFrameworkOp curatorFrameworkOp, AbnormalJob abnormalJob) {
		String jobName = abnormalJob.getJobName();
		String serverNodePath = JobNodePath.getServerNodePath(jobName);
		if (curatorFrameworkOp.checkExists(serverNodePath)) {
			List<String> servers = curatorFrameworkOp.getChildren(serverNodePath);
			if (servers != null && !servers.isEmpty()) {
				for (String server : servers) {
					if (curatorFrameworkOp.checkExists(JobNodePath.getServerStatus(jobName, server))) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private void reportAlarmAbnormalJobIfNecessary(CuratorRepository.CuratorFrameworkOp curatorFrameworkOp,
			AbnormalJob abnormalJob) {
		boolean rerun = getRerun(abnormalJob.getJobName(), curatorFrameworkOp);
		if (rerun) {
			if (abnormalJob.isHasRerun()) {
				reportAlarmIfNotRead(abnormalJob);
			} else {
				String namespace = abnormalJob.getDomainName();
				String jobName = abnormalJob.getJobName();
				try {
					log.warn("found abnormal job:{}, will runAtOnce", abnormalJob);
					jobService.runAtOnce(namespace, jobName);
				} catch (Throwable t) {
					log.warn(String.format("rerun job error, namespace:%s, jobName:%s", namespace, jobName), t);
					reportAlarmIfNotRead(abnormalJob);
				} finally {
					abnormalJob.setHasRerun(true);
				}
			}
		} else {
			reportAlarmIfNotRead(abnormalJob);
		}
	}

	private void reportAlarmIfNotRead(AbnormalJob abnormalJob) {
		if (!abnormalJob.isRead()) {
			addNeedReportAlarmJob(abnormalJob);
			// ??????????????????????????????????????????????????????
			String namespace = abnormalJob.getDomainName();
			String jobName = abnormalJob.getJobName();
			try {
				reportAlarmService.dashboardAbnormalJob(namespace, jobName, abnormalJob.getTimeZone(),
						abnormalJob.getNextFireTime());
			} catch (Throwable t) {
				log.error(
						String.format("report alarm abnormal job error, namespace:%s, jobName:%s", namespace, jobName),
						t);
			}
		}
	}

	/**
	 * ??????????????????
	 * <p>
	 * ????????? 1??????????????????stock-update??????????????????????????????????????????????????????????????????????????????????????????????????????????????? 2??????running?????????????????????
	 * 3.1??????completed??????????????????????????????Mtime?????????????????????????????? 3.2?????????Mtime???????????????????????????????????????????????????????????????????????????+??????, ??????????????????????????????
	 * 4????????????running??????completed????????????
	 *
	 * @return -1?????????????????????-1???????????????
	 */
	private long checkShardingItemState(CuratorRepository.CuratorFrameworkOp curatorFrameworkOp,
			AbnormalJob abnormalJob, String enabledPath, String shardingItemStr) {
		List<String> itemChildren = curatorFrameworkOp
				.getChildren(JobNodePath.getExecutionItemNodePath(abnormalJob.getJobName(), shardingItemStr));

		// ???????????????stock-update???????????????????????????????????????????????????????????????????????????????????????????????????????????????
		if (itemChildren.size() == 2) {
			return -1;
		}
		// ???running?????????????????????
		if (itemChildren.contains("running")) {
			return -1;
		}

		// ???completed?????????????????????????????????Mtime??????
		// 1?????????????????????Mtime???????????????????????????????????????????????????????????????????????????+??????, ??????????????????????????????
		// 2??????????????????0?????????completed????????????????????????????????????????????????????????????????????????????????????????????????
		if (itemChildren.contains("completed")) {
			return checkShardingItemStateWhenIsCompleted(curatorFrameworkOp, abnormalJob, enabledPath, shardingItemStr);
		} else { // ?????????running??????completed????????????
			return checkShardingItemStateWhenNotCompleted(curatorFrameworkOp, abnormalJob, enabledPath,
					shardingItemStr);
		}
	}

	private long checkShardingItemStateWhenNotCompleted(CuratorRepository.CuratorFrameworkOp curatorFrameworkOp,
			AbnormalJob abnormalJob, String enabledPath, String shardingItemStr) {
		if (abnormalJob.getNextFireTimeAfterEnabledMtimeOrLastCompleteTime() == 0) {
			long nextFireTimeAfterThis = curatorFrameworkOp.getMtime(enabledPath);
			long lastCompleteTime = getLastCompleteTime(curatorFrameworkOp, abnormalJob.getJobName(), shardingItemStr);
			if (nextFireTimeAfterThis < lastCompleteTime) {
				nextFireTimeAfterThis = lastCompleteTime;
			}

			abnormalJob.setNextFireTimeAfterEnabledMtimeOrLastCompleteTime(
					getNextFireTimeAfterSpecifiedTimeExcludePausePeriod(nextFireTimeAfterThis, abnormalJob.getJobName(),
							curatorFrameworkOp));
		}
		Long nextFireTime = abnormalJob.getNextFireTimeAfterEnabledMtimeOrLastCompleteTime();
		// ??????????????????????????????????????????+??????, ??????????????????????????????
		if (nextFireTime != null && nextFireTime + DashboardConstants.ALLOW_DELAY_MILLIONSECONDS < System
				.currentTimeMillis()) {
			return nextFireTime;
		}
		return -1;
	}

	private long checkShardingItemStateWhenIsCompleted(CuratorRepository.CuratorFrameworkOp curatorFrameworkOp,
			AbnormalJob abnormalJob, String enabledPath, String shardingItemStr) {
		long currentTime = System.currentTimeMillis();
		String completedPath = JobNodePath.getExecutionNodePath(abnormalJob.getJobName(), shardingItemStr, "completed");
		long completedMtime = curatorFrameworkOp.getMtime(completedPath);

		if (completedMtime > 0) {
			// ??????minCompletedMtime???enabled mtime, ????????????
			long nextFireTimeAfterThis = curatorFrameworkOp.getMtime(enabledPath);
			if (nextFireTimeAfterThis < completedMtime) {
				nextFireTimeAfterThis = completedMtime;
			}

			Long nextFireTimeExcludePausePeriod = getNextFireTimeAfterSpecifiedTimeExcludePausePeriod(
					nextFireTimeAfterThis, abnormalJob.getJobName(), curatorFrameworkOp);
			// ??????????????????????????????????????????+??????, ??????????????????????????????
			if (nextFireTimeExcludePausePeriod != null
					&& nextFireTimeExcludePausePeriod + DashboardConstants.ALLOW_DELAY_MILLIONSECONDS < currentTime) {
				// ???????????????????????????????????????delta??????????????????
				if (!doubleCheckShardingStateAfterAddingDeltaInterval(curatorFrameworkOp, abnormalJob,
						nextFireTimeAfterThis, nextFireTimeExcludePausePeriod, currentTime)) {
					log.debug("still has problem after adding delta interval");
					return nextFireTimeExcludePausePeriod;
				} else {
					return -1;
				}
			}
		}
		return -1;
	}

	/**
	 * ????????????executor?????????Console?????????????????????????????????????????????????????????nextFireTime + ALLOW_DELAY_MILLIONSECONDS ???????????????????????????
	 *
	 * @return false: ??????????????????true: ????????????????????????
	 */
	private boolean doubleCheckShardingStateAfterAddingDeltaInterval(
			CuratorRepository.CuratorFrameworkOp curatorFrameworkOp, AbnormalJob abnormalJob,
			long nextFireTimeAfterThis, Long nextFireTimeExcludePausePeriod, long currentTime) {
		Long nextFireTimeExcludePausePeriodWithDelta = getNextFireTimeAfterSpecifiedTimeExcludePausePeriod(
				nextFireTimeAfterThis + DashboardConstants.INTERVAL_DELTA_IN_SECOND, abnormalJob.getJobName(),
				curatorFrameworkOp);

		if (nextFireTimeExcludePausePeriod.equals(nextFireTimeExcludePausePeriodWithDelta)
				|| nextFireTimeExcludePausePeriodWithDelta + DashboardConstants.ALLOW_DELAY_MILLIONSECONDS
				< currentTime) {
			log.debug("still not work after adding delta interval");
			return false;
		}

		return true;
	}

	private Long getNextFireTimeAfterSpecifiedTimeExcludePausePeriod(long nextFireTimeAfterThis, String jobName,
			CuratorRepository.CuratorFrameworkOp curatorFrameworkOp) {
		String cronPath = JobNodePath.getConfigNodePath(jobName, "cron");
		String cronVal = curatorFrameworkOp.getData(cronPath);
		CronExpression cronExpression = null;
		try {
			cronExpression = new CronExpression(cronVal);
		} catch (ParseException e) {
			log.error(e.getMessage(), e);
			return null;
		}
		String timeZoneStr = getTimeZone(jobName, curatorFrameworkOp);
		TimeZone timeZone = TimeZone.getTimeZone(timeZoneStr);
		cronExpression.setTimeZone(timeZone);

		Date nextFireTime = cronExpression.getTimeAfter(new Date(nextFireTimeAfterThis));
		String pausePeriodDatePath = JobNodePath.getConfigNodePath(jobName, "pausePeriodDate");
		String pausePeriodDate = curatorFrameworkOp.getData(pausePeriodDatePath);
		String pausePeriodTimePath = JobNodePath.getConfigNodePath(jobName, "pausePeriodTime");
		String pausePeriodTime = curatorFrameworkOp.getData(pausePeriodTimePath);

		while (nextFireTime != null && isInPausePeriod(nextFireTime, pausePeriodDate, pausePeriodTime, timeZone)) {
			nextFireTime = cronExpression.getTimeAfter(nextFireTime);
		}
		if (null == nextFireTime) {
			return null;
		}
		return nextFireTime.getTime();
	}

	private String getTimeZone(String jobName, CuratorRepository.CuratorFrameworkOp curatorFrameworkOp) {
		String timeZoneStr = curatorFrameworkOp.getData(JobNodePath.getConfigNodePath(jobName, "timeZone"));
		if (timeZoneStr == null || timeZoneStr.trim().length() == 0) {
			timeZoneStr = SaturnConstants.TIME_ZONE_ID_DEFAULT;
		}
		return timeZoneStr;
	}

	private boolean getRerun(String jobName, CuratorRepository.CuratorFrameworkOp curatorFrameworkOp) {
		String rerun = curatorFrameworkOp.getData(JobNodePath.getConfigNodePath(jobName, CONFIG_ITEM_RERUN));
		return Boolean.valueOf(rerun);
	}

	/**
	 * ????????????
	 */
	public void reportAlarmOutdatedNoRunningJobs() {
		Map<String, List<Map<String, String>>> abnormalJobMap = new HashMap<>();
		for (AbnormalJob abnormalJob : needReportAlarmJobs) {
			String namespace = abnormalJob.getDomainName();
			if (!abnormalJobMap.containsKey(namespace)) {
				abnormalJobMap.put(namespace, new ArrayList<Map<String, String>>());
			}
			Map<String, String> customMap = new HashMap<>();
			customMap.put("job", abnormalJob.getJobName());
			customMap.put("timeZone", abnormalJob.getTimeZone());
			customMap.put("shouldFiredTime", String.valueOf(abnormalJob.getNextFireTime()));
			abnormalJobMap.get(namespace).add(customMap);
		}
		for (String namespace : abnormalJobMap.keySet()) {
			raiseAlarmPerNamespace(abnormalJobMap.get(namespace), namespace);
		}
	}

	private void raiseAlarmPerNamespace(List<Map<String, String>> jobs, String namespace) {
		try {
			reportAlarmService.dashboardAbnormalBatchJobs(namespace, jobs);
		} catch (ReportAlarmException e) {
			log.error(String.format("batch report alarm abnormal job error, namespace:%s, jobs:%s", namespace,
					getJobNamesString(jobs)), e);
		}
	}

	private String getJobNamesString(List<Map<String, String>> jobs) {
		StringBuilder jobNames = new StringBuilder();
		for (int i = 0, size = jobs.size(); i < size; i++) {
			Map<String, String> job = jobs.get(i);
			jobNames.append(job.get("job"));
			if (i < size - 1) {
				jobNames.append(',');
			}
		}
		return jobNames.toString();
	}

	public List<AbnormalJob> getOutdatedNoRunningJobs() {
		return new ArrayList<>(outdatedNoRunningJobs);
	}

	public void setAbnormalShardingStateCache(Map<String, AbnormalShardingState> abnormalShardingStateCache) {
		this.abnormalShardingStateCache = abnormalShardingStateCache;
	}

	public void setReportAlarmService(ReportAlarmService reportAlarmService) {
		this.reportAlarmService = reportAlarmService;
	}

	public void setJobService(JobService jobService) {
		this.jobService = jobService;
	}
}
