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

package com.vip.saturn.job.console.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vip.saturn.job.console.repository.zookeeper.CuratorRepository;
import com.vip.saturn.job.sharding.listener.AbstractConnectionListener;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author chembo.huang
 */
public class ZkCluster implements Serializable {

	private static final long serialVersionUID = 1L;

	private String zkClusterKey;

	private String zkAlias;

	private String zkAddr;

	private String description;

	private String digest;

	private boolean offline = false;

	@JsonIgnore
	private transient CuratorRepository.CuratorFrameworkOp curatorFrameworkOp;

	@JsonIgnore
	private transient AbstractConnectionListener connectionListener;

	@JsonIgnore
	private ArrayList<RegistryCenterConfiguration> regCenterConfList = new ArrayList<>();

	public ZkCluster() {
	}

	public String getZkClusterKey() {
		return zkClusterKey;
	}

	public void setZkClusterKey(String zkClusterKey) {
		this.zkClusterKey = zkClusterKey;
	}

	public String getZkAlias() {
		return zkAlias;
	}

	public void setZkAlias(String zkAlias) {
		this.zkAlias = zkAlias;
	}

	public String getZkAddr() {
		return zkAddr;
	}

	public void setZkAddr(String zkAddr) {
		this.zkAddr = zkAddr;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getDigest() {
		return digest;
	}

	public void setDigest(String digest) {
		this.digest = digest;
	}

	public boolean isOffline() {
		return offline;
	}

	public void setOffline(boolean offline) {
		this.offline = offline;
	}

	public CuratorRepository.CuratorFrameworkOp getCuratorFrameworkOp() {
		return curatorFrameworkOp;
	}

	public void setCuratorFrameworkOp(CuratorRepository.CuratorFrameworkOp curatorFrameworkOp) {
		this.curatorFrameworkOp = curatorFrameworkOp;
	}

	public AbstractConnectionListener getConnectionListener() {
		return connectionListener;
	}

	public void setConnectionListener(AbstractConnectionListener connectionListener) {
		this.connectionListener = connectionListener;
	}

	public ArrayList<RegistryCenterConfiguration> getRegCenterConfList() {
		return regCenterConfList;
	}

	public void setRegCenterConfList(ArrayList<RegistryCenterConfiguration> regCenterConfList) {
		this.regCenterConfList = regCenterConfList;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ZkCluster)) {
			return false;
		}

		ZkCluster zkCluster = (ZkCluster) o;

		if (zkClusterKey != null ? !zkClusterKey.equals(zkCluster.zkClusterKey) : zkCluster.zkClusterKey != null) {
			return false;
		}
		if (zkAlias != null ? !zkAlias.equals(zkCluster.zkAlias) : zkCluster.zkAlias != null) {
			return false;
		}
		if (zkAddr != null ? !zkAddr.equals(zkCluster.zkAddr) : zkCluster.zkAddr != null) {
			return false;
		}
		if (description != null ? !description.equals(zkCluster.description) : zkCluster.description != null) {
			return false;
		}
		return digest != null ? digest.equals(zkCluster.digest) : zkCluster.digest == null;

	}


	/**
	 * ????????????????????????????????????
	 */
	public boolean equalsNoNeedReconnect(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ZkCluster)) {
			return false;
		}

		ZkCluster zkCluster = (ZkCluster) o;

		if (zkClusterKey != null ? !zkClusterKey.equals(zkCluster.zkClusterKey) : zkCluster.zkClusterKey != null) {
			return false;
		}
		if (zkAlias != null ? !zkAlias.equals(zkCluster.zkAlias) : zkCluster.zkAlias != null) {
			return false;
		}
		if (zkAddr != null ? !zkAddr.equals(zkCluster.zkAddr) : zkCluster.zkAddr != null) {
			return false;
		}
		return digest != null ? digest.equals(zkCluster.digest) : zkCluster.digest == null;

	}

	@Override
	public int hashCode() {
		int result = zkClusterKey != null ? zkClusterKey.hashCode() : 0;
		result = 31 * result + (zkAlias != null ? zkAlias.hashCode() : 0);
		result = 31 * result + (zkAddr != null ? zkAddr.hashCode() : 0);
		result = 31 * result + (description != null ? description.hashCode() : 0);
		result = 31 * result + (digest != null ? digest.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "ZkCluster{" + "zkClusterKey='" + zkClusterKey + '\'' + ", zkAlias='" + zkAlias + '\'' + ", zkAddr='"
				+ zkAddr + '\'' + ", description='" + description + '\'' + ", digest='" + digest + '\'' + ", offline="
				+ offline + ", regCenterConfList=" + regCenterConfList + '}';
	}
}
