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

package com.vip.saturn.job.shell;

import com.vip.saturn.job.SaturnJobReturn;
import com.vip.saturn.job.SaturnSystemErrorGroup;
import com.vip.saturn.job.SaturnSystemReturnCode;
import com.vip.saturn.job.basic.AbstractSaturnJob;
import com.vip.saturn.job.basic.SaturnConstant;
import com.vip.saturn.job.basic.SaturnExecutionContext;
import com.vip.saturn.job.utils.JsonUtils;
import com.vip.saturn.job.utils.LogUtils;
import com.vip.saturn.job.utils.ScriptPidUtils;
import com.vip.saturn.job.utils.SystemEnvProperties;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class ScriptJobRunner {

	private static Logger log = LoggerFactory.getLogger(ScriptJobRunner.class);

	private static final String PREFIX_COMAND = " source /etc/profile; ";

	private Map<String, String> envMap = new HashMap<>();

	private AbstractSaturnJob job;

	private Integer item;

	private String itemValue;

	private SaturnExecutionContext saturnExecutionContext;

	private String jobName;

	private SaturnExecuteWatchdog watchdog;

	private boolean businessReturned = false;

	private File saturnOutputFile;

	public ScriptJobRunner(Map<String, String> envMap, AbstractSaturnJob job, Integer item, String itemValue,
			SaturnExecutionContext saturnExecutionContext) {
		if (envMap != null) {
			this.envMap.putAll(envMap);
		}
		this.job = job;
		this.item = item;
		this.itemValue = itemValue;
		this.saturnExecutionContext = saturnExecutionContext;
		if (job != null) {
			this.jobName = job.getJobName();
		}
	}

	public boolean isBusinessReturned() {
		return businessReturned;
	}

	private void createSaturnJobReturnFile() throws IOException {
		if (envMap.containsKey(SystemEnvProperties.NAME_VIP_SATURN_OUTPUT_PATH)) {
			String saturnOutputPath = envMap.get(SystemEnvProperties.NAME_VIP_SATURN_OUTPUT_PATH);
			saturnOutputFile = new File(saturnOutputPath);
			if (!saturnOutputFile.exists()) {
				FileUtils.forceMkdir(saturnOutputFile.getParentFile());
				if (!saturnOutputFile.createNewFile()) {
					LogUtils.warn(log, jobName, "file {} already exsits.", saturnOutputPath);
				}
			}
		}
	}

	private CommandLine createCommandLine(Map<String, String> env) {
		StringBuilder envStringBuilder = new StringBuilder();
		if (envMap != null && !envMap.isEmpty()) {
			for (Entry<String, String> envEntrySet : envMap.entrySet()) {
				envStringBuilder.append("export ").append(envEntrySet.getKey()).append('=')
						.append(envEntrySet.getValue()).append(';');
			}
		}
		String execParameter =
				envStringBuilder.toString() + PREFIX_COMAND + ScriptPidUtils.filterEnvInCmdStr(env, itemValue);
		final CommandLine cmdLine = new CommandLine("/bin/sh");
		cmdLine.addArguments(new String[]{"-c", execParameter}, false);
		return cmdLine;
	}

	private SaturnJobReturn readSaturnJobReturn() {
		SaturnJobReturn tmp = null;
		if (saturnOutputFile != null && saturnOutputFile.exists()) {
			try {
				String fileContents = FileUtils.readFileToString(saturnOutputFile);
				if (StringUtils.isNotBlank(fileContents)) {
					tmp = JsonUtils.getGson().fromJson(fileContents.trim(), SaturnJobReturn.class);
					businessReturned = true; // ????????????????????????
				}
			} catch (Throwable t) {
				LogUtils.error(log, jobName, "{} - {} read SaturnJobReturn from {} error", jobName, item,
						saturnOutputFile.getAbsolutePath(), t);
				tmp = new SaturnJobReturn(SaturnSystemReturnCode.USER_FAIL, "Exception: " + t,
						SaturnSystemErrorGroup.FAIL);
			}
		}
		return tmp;
	}

	public synchronized SaturnExecuteWatchdog getWatchdog() {
		if (watchdog == null) {
			long timeoutSeconds = saturnExecutionContext.getTimetoutSeconds();
			String executorName = job.getExecutorName();
			if (timeoutSeconds > 0) {
				watchdog = new SaturnExecuteWatchdog(timeoutSeconds * 1000, jobName, item, itemValue, executorName);
				LogUtils.info(log, jobName, "Job {} enable timeout control : {} s ", jobName, timeoutSeconds);
			} else { // ????????????????????????????????????watchdog: ???????????????5???
				watchdog = new SaturnExecuteWatchdog(5L * 365 * 24 * 3600 * 1000, jobName, item, itemValue,
						executorName);
				if (log.isDebugEnabled()) {
					LogUtils.debug(log, jobName, "Job {} disable timeout control", jobName);
				}
			}
		}
		return watchdog;
	}

	public SaturnJobReturn runJob() {
		SaturnJobReturn saturnJobReturn = null;
		long timeoutSeconds = saturnExecutionContext.getTimetoutSeconds();
		try {
			createSaturnJobReturnFile();
			saturnJobReturn = execute(timeoutSeconds);
		} catch (Throwable t) {
			LogUtils.error(log, jobName, "{} - {} Exception", jobName, item, t);
			saturnJobReturn = new SaturnJobReturn(SaturnSystemReturnCode.SYSTEM_FAIL, "Exception: " + t,
					SaturnSystemErrorGroup.FAIL);
		} finally {
			FileUtils.deleteQuietly(saturnOutputFile.getParentFile());
		}

		if (saturnJobReturn.getProp() == null) {
			saturnJobReturn.setProp(new HashMap());
		}
		return saturnJobReturn;
	}

	private SaturnJobReturn execute(long timeoutSeconds) {
		SaturnJobReturn saturnJobReturn;
		ProcessOutputStream processOutputStream = new ProcessOutputStream(1);
		DefaultExecutor executor = new DefaultExecutor();
		PumpStreamHandler streamHandler = new PumpStreamHandler(processOutputStream);
		streamHandler.setStopTimeout(timeoutSeconds * 1000); // ????????????????????????, (??????commons-exec???????????????2??????addition)
		executor.setExitValue(0);
		executor.setStreamHandler(streamHandler);
		executor.setWatchdog(getWatchdog());

		// filter env key in execParameter. like cd ${mypath} -> cd /root/my.
		Map<String, String> env = ScriptPidUtils.loadEnv();
		CommandLine commandLine = createCommandLine(env);

		try {
			long start = System.currentTimeMillis();
			LogUtils.info(log, jobName, "Begin executing {}-{} {}", jobName, item, commandLine);
			int exitValue = executor.execute(commandLine, env);
			long end = System.currentTimeMillis();
			LogUtils.info(log, jobName, "Finish executing {}-{} {}, the exit value is {}, cost={}ms", jobName, item,
					commandLine, exitValue, (end - start));

			SaturnJobReturn tmp = readSaturnJobReturn();
			if (tmp == null) {
				tmp = new SaturnJobReturn("the exit value is " + exitValue);
			}
			saturnJobReturn = tmp;
		} catch (Exception e) {
			saturnJobReturn = handleException(timeoutSeconds, e);
		} finally {
			try {
				// ?????????set???jobLog, ?????????zk??????ExecutionService??????
				handleJobLog(processOutputStream.getJobLog());
				processOutputStream.close();
			} catch (Exception ex) {
				LogUtils.error(log, jobName, "{}-{} Error at closing output stream. Should not be concern: {}", jobName,
						item, ex.getMessage(), ex);
			}
			stopStreamHandler(streamHandler);
			ScriptPidUtils.removePidFile(job.getExecutorName(), jobName, "" + item, watchdog.getPid());
		}
		return saturnJobReturn;
	}

	private void handleJobLog(String jobLog) {
		// ???????????????????????????jobLog????????????1M
		if (jobLog != null && jobLog.length() > SaturnConstant.MAX_JOB_LOG_DATA_LENGTH) {
			LogUtils.info(log, jobName,
					"As the job log exceed max length, only the previous {} characters will be reported",
					SaturnConstant.MAX_JOB_LOG_DATA_LENGTH);
			jobLog = jobLog.substring(0, SaturnConstant.MAX_JOB_LOG_DATA_LENGTH);
		}

		saturnExecutionContext.putJobLog(item, jobLog);

		// ?????????saturn-job-executor.log????????????shell??????jobLog????????????????????????????????????????????????????????????
		System.out.println("[" + jobName + "] msg=" + jobName + "-" + item + ":" + jobLog);// NOSONAR

		LogUtils.info(log, jobName, "{}-{}: {}", jobName, item, jobLog);
	}

	private void stopStreamHandler(PumpStreamHandler streamHandler) {
		try {
			streamHandler.stop();
		} catch (IOException ex) {
			LogUtils.debug(log, jobName, "{}-{} Error at closing log stream. Should not be concern: {}", jobName, item,
					ex.getMessage(), ex);
		}
	}

	private SaturnJobReturn handleException(long timeoutSeconds, Exception e) {
		SaturnJobReturn saturnJobReturn;
		String errMsg = e.toString();
		if (watchdog.isTimeout()) {
			saturnJobReturn = new SaturnJobReturn(SaturnSystemReturnCode.SYSTEM_FAIL,
					String.format("execute job timeout(%sms), %s", timeoutSeconds * 1000, errMsg),
					SaturnSystemErrorGroup.TIMEOUT);
			LogUtils.error(log, jobName, "{}-{} timeout, {}", jobName, item, errMsg);
			return saturnJobReturn;
		}

		if (watchdog.isForceStop()) {
			saturnJobReturn = new SaturnJobReturn(SaturnSystemReturnCode.SYSTEM_FAIL,
					"the job was forced to stop, " + errMsg, SaturnSystemErrorGroup.FAIL);
			LogUtils.error(log, jobName, "{}-{} force stopped, {}", jobName, item, errMsg);
			return saturnJobReturn;
		}

		saturnJobReturn = new SaturnJobReturn(SaturnSystemReturnCode.USER_FAIL, "Exception: " + errMsg,
				SaturnSystemErrorGroup.FAIL);
		LogUtils.error(log, jobName, "{}-{} Exception: {}", jobName, item, errMsg, e);
		return saturnJobReturn;
	}
}
