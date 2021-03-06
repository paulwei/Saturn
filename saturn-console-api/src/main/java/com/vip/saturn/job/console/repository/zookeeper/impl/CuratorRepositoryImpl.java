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

package com.vip.saturn.job.console.repository.zookeeper.impl;

import com.google.common.base.Strings;
import com.vip.saturn.job.console.exception.JobConsoleException;
import com.vip.saturn.job.console.repository.zookeeper.CuratorRepository;
import com.vip.saturn.job.console.utils.ThreadLocalCuratorClient;
import com.vip.saturn.job.sharding.utils.CuratorUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.CuratorFrameworkFactory.Builder;
import org.apache.curator.framework.api.ACLProvider;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.curator.framework.api.transaction.CuratorTransactionResult;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class CuratorRepositoryImpl implements CuratorRepository {

	private static final int WAITING_SECONDS = 2;
	protected static Logger log = LoggerFactory.getLogger(CuratorRepositoryImpl.class);
	/**
	 * ??????????????????
	 */
	private static final int SESSION_TIMEOUT = 20 * 1000;

	/**
	 * ??????????????????
	 */
	private static final int CONNECTION_TIMEOUT = 20 * 1000;

	@Override
	public CuratorFramework connect(final String connectString, final String namespace, final String digest) {
		Builder builder = CuratorFrameworkFactory.builder().connectString(connectString)
				.sessionTimeoutMs(SESSION_TIMEOUT).connectionTimeoutMs(CONNECTION_TIMEOUT)
				.retryPolicy(new ExponentialBackoffRetry(1000, 3, 3000));
		if (namespace != null) {
			builder.namespace(namespace);
		}
		if (!Strings.isNullOrEmpty(digest)) {
			builder.authorization("digest", digest.getBytes(Charset.forName("UTF-8"))).aclProvider(new ACLProvider() {

				@Override
				public List<ACL> getDefaultAcl() {
					return ZooDefs.Ids.CREATOR_ALL_ACL;
				}

				@Override
				public List<ACL> getAclForPath(final String path) {
					return ZooDefs.Ids.CREATOR_ALL_ACL;
				}
			});
		}
		CuratorFramework client = builder.build();
		client.start();
		boolean established = false;
		try {
			established = client.blockUntilConnected(WAITING_SECONDS, TimeUnit.SECONDS);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		if (established) {
			return client;
		}
		CloseableUtils.closeQuietly(client);
		return null;
	}

	@Override
	public CuratorFrameworkOp inSessionClient() {
		return new CuratorFrameworkOpImpl(ThreadLocalCuratorClient.getCuratorClient());
	}

	@Override
	public CuratorFrameworkOp newCuratorFrameworkOp(CuratorFramework curatorFramework) {
		return new CuratorFrameworkOpImpl(curatorFramework);
	}

	static class CuratorFrameworkOpImpl implements CuratorFrameworkOp {

		private CuratorFramework curatorFramework;

		public CuratorFrameworkOpImpl(CuratorFramework curatorFramework) {
			this.curatorFramework = curatorFramework;
		}

		@Override
		public boolean checkExists(final String node) {
			try {
				return null != curatorFramework.checkExists().forPath(node);
				// CHECKSTYLE:OFF
			} catch (final Exception ex) {
				// CHECKSTYLE:ON
				throw new JobConsoleException(ex);
			}
		}

		@Override
		public String getData(final String node) {
			try {
				if (checkExists(node)) {
					byte[] nodeData = curatorFramework.getData().forPath(node);
					if (nodeData == null) {// executor?????????????????????????????????????????????sharding??????????????????????????????null????????????null??????????????????new
						// String????????????????????????
						return null;
					}
					return new String(nodeData, Charset.forName("UTF-8"));
				} else {
					return null;
				}
			} catch (final NoNodeException ignore) {
				return null;
				// CHECKSTYLE:OFF
			} catch (final Exception ex) {
				// CHECKSTYLE:ON
				throw new JobConsoleException(ex);
			}
		}

		@Override
		public List<String> getChildren(final String node) {
			try {
				return curatorFramework.getChildren().forPath(node);
				// CHECKSTYLE:OFF
			} catch (final NoNodeException ignore) {
				return null;
				// CHECKSTYLE:OFF
			} catch (final Exception ex) {
				// CHECKSTYLE:ON
				throw new JobConsoleException(ex);
			}
		}

		@Override
		public void create(final String node) {
			create(node, "");
		}

		@Override
		public void create(final String node, Object value) {
			if (value == null) {
				log.info("node value is null, won't create, node: {}", node);
				return;
			}
			try {
				curatorFramework.create().creatingParentsIfNeeded()
						.forPath(node, value.toString().getBytes(Charset.forName("UTF-8")));
			} catch (final NodeExistsException ignore) {
				// CHECKSTYLE:OFF
			} catch (final Exception ex) {
				// CHECKSTYLE:ON
				throw new JobConsoleException(ex);
			}
		}

		@Override
		public void update(final String node, final Object value) {
			if (value == null) {
				log.info("node value is null, won't update, node: {}", node);
				return;
			}
			try {
				if (this.checkExists(node)) {
					curatorFramework.inTransaction().check().forPath(node).and().setData()
							.forPath(node, value.toString().getBytes(Charset.forName("UTF-8"))).and().commit();
				} else {
					this.create(node, value);
				}
			} catch (final NoNodeException ignore) {
				// CHECKSTYLE:OFF
			} catch (final Exception ex) {
				// CHECKSTYLE:ON
				throw new JobConsoleException(ex);
			}
		}

		@Override
		public void delete(final String node) {
			try {
				if (null != curatorFramework.checkExists().forPath(node)) {
					curatorFramework.delete().forPath(node);
				}
			} catch (final NoNodeException ignore) {
				// CHECKSTYLE:OFF
			} catch (final Exception ex) {
				// CHECKSTYLE:ON
				throw new JobConsoleException(ex);
			}
		}

		@Override
		public void deleteRecursive(final String node) {
			try {
				if (null != curatorFramework.checkExists().forPath(node)) {
					CuratorUtils.deletingChildrenIfNeeded(curatorFramework, node);
				}
			} catch (final NoNodeException ignore) {
				// CHECKSTYLE:OFF
			} catch (final Exception ex) {
				// CHECKSTYLE:ON
				throw new JobConsoleException(ex);
			}
		}

		/**
		 * ??????????????????????????????????????????.
		 *
		 * @param node  ??????????????????
		 * @param value ?????????????????????
		 */
		@Override
		public void fillJobNodeIfNotExist(final String node, final Object value) {
			if (value == null) {
				log.info("node value is null, won't fillJobNodeIfNotExist, node: {}", node);
				return;
			}
			if (!checkExists(node)) {
				try {
					curatorFramework.create().creatingParentsIfNeeded()
							.forPath(node, value.toString().getBytes(Charset.forName("UTF-8")));
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}

		@Override
		public Stat getStat(String node) {
			try {
				return curatorFramework.checkExists().forPath(node);
			} catch (final Exception ex) {
				// CHECKSTYLE:ON
				throw new JobConsoleException(ex);
			}
		}

		@Override
		public long getMtime(String node) {
			try {
				Stat stat = curatorFramework.checkExists().forPath(node);
				if (stat != null) {
					return stat.getMtime();
				} else {
					return 0L;
				}
			} catch (final Exception ex) {
				// CHECKSTYLE:ON
				throw new JobConsoleException(ex);
			}
		}

		@Override
		public long getCtime(String node) {
			try {
				Stat stat = curatorFramework.checkExists().forPath(node);
				if (stat != null) {
					return stat.getCtime();
				} else {
					return 0L;
				}
			} catch (final Exception ex) {
				// CHECKSTYLE:ON
				throw new JobConsoleException(ex);
			}
		}

		/**
		 * ?????????check?????????
		 */
		@Override
		public CuratorTransactionOp inTransaction() {
			try {
				return new CuratorTransactionOpImpl(curatorFramework);
			} catch (Exception ex) {
				// CHECKSTYLE:ON
				throw new JobConsoleException(ex);
			}
		}

		@Override
		public CuratorFramework getCuratorFramework() {
			return curatorFramework;
		}

		static class CuratorTransactionOpImpl implements CuratorTransactionOp {

			private CuratorTransactionFinal curatorTransactionFinal;
			private CuratorFramework curatorClient;

			public CuratorTransactionOpImpl(CuratorFramework curatorClient) {
				this.curatorClient = curatorClient;
				try {
					curatorTransactionFinal = curatorClient.inTransaction().check().forPath("/").and();
				} catch (final Exception ex) {
					throw new JobConsoleException(ex);
				}
			}

			private boolean checkExists(String node) throws Exception {
				return curatorClient.checkExists().forPath(node) != null;
			}

			private CuratorTransactionOpImpl create(String node, byte[] data) throws Exception {
				curatorTransactionFinal = curatorTransactionFinal.create().withMode(CreateMode.PERSISTENT)
						.forPath(node, data).and();
				return this;
			}

			private byte[] getData(String node) throws Exception {
				return curatorClient.getData().forPath(node);
			}

			private boolean bytesEquals(byte[] a, byte[] b) {
				if (a == null || b == null) {
					return (a == null && b == null);
				}
				if (a.length != b.length) {
					return false;
				}
				for (int i = 0, size = a.length; i < size; i++) {
					if (a[i] != b[i]) {
						return false;
					}
				}
				return true;
			}

			@Override
			public CuratorTransactionOpImpl replace(String node, Object value) throws Exception {
				if (value == null) {
					log.info("node value is null, won't replace, node: {}", node);
					return this;
				}
				byte[] data = value.toString().getBytes(Charset.forName("UTF-8"));
				curatorTransactionFinal = curatorTransactionFinal.setData().forPath(node, data).and();
				return this;
			}

			@Override
			public CuratorTransactionOpImpl replaceIfChanged(String node, Object value) throws Exception {
				if (value == null) {
					log.info("node value is null, won't replaceIfChanged, node: {}", node);
					return this;
				}
				byte[] newData = value.toString().getBytes(Charset.forName("UTF-8"));
				if (this.checkExists(node)) {
					byte[] oldData = this.getData(node);
					if (!bytesEquals(newData, oldData)) {
						curatorTransactionFinal = curatorTransactionFinal.check().forPath(node).and().setData()
								.forPath(node, newData).and();
					}
				} else {
					this.create(node, newData);
				}
				return this;
			}

			@Override
			public CuratorTransactionOp create(String node, Object value) throws Exception {
				if (value == null) {
					log.info("node value is null, won't create, node: {}", node);
					return this;
				}
				byte[] data = value.toString().getBytes(Charset.forName("UTF-8"));
				curatorTransactionFinal = curatorTransactionFinal.create().withMode(CreateMode.PERSISTENT)
						.forPath(node, data).and();
				return this;
			}

			@Override
			public CuratorTransactionOp delete(String node) throws Exception {
				curatorTransactionFinal = curatorTransactionFinal.delete().forPath(node).and();
				return this;
			}

			@Override
			public Collection<CuratorTransactionResult> commit() throws Exception {
				return curatorTransactionFinal.commit();
			}
		}

	}

}
