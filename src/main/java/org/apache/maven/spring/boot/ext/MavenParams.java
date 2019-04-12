/*
 * Copyright (c) 2018, vindell (https://github.com/vindell).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.maven.spring.boot.ext;

/**
 * TODO
 * 
 * @author ： <a href="https://github.com/vindell">vindell</a>
 */
public class MavenParams {
	/**
	 * jar包在maven仓库中的groupId
	 */
	private String groupId;
	/**
	 * jar包在maven仓库中的artifactId
	 */
	private String artifactId;
	/**
	 * jar包在maven仓库中的version
	 */
	private String version;
	/**
	 * 远程maven仓库的URL地址，默认使用bw30的远程maven-public库
	 */
	private String repository = "http://ae.mvn.bw30.com/repository/maven-public/";
	/**
	 * 下载的jar包存放的目标地址，默认为./target/repo
	 */
	private String target = "temp";
	/**
	 * 登录远程maven仓库的用户名，若远程仓库不需要权限，设为null，默认为null
	 */
	private String username = null;
	/**
	 * 登录远程maven仓库的密码，若远程仓库不需要权限，设为null，默认为null
	 */
	private String password = null;

	public MavenParams() {
		super();
	}

	public MavenParams(String groupId, String artifactId) {
		super();
		this.groupId = groupId;
		this.artifactId = artifactId;
	}

	public MavenParams(String groupId, String artifactId, String username, String password) {
		super();
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.username = username;
		this.password = password;
	}

	public MavenParams(String groupId, String artifactId, String version, String repository,
			/* String target, */ String username, String password) {
		super();
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		this.repository = repository;
		/* this.target = target; */
		this.username = username;
		this.password = password;
	}

	public MavenParams(String groupId, String artifactId, String version, String username, String password) {
		super();
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		this.username = username;
		this.password = password;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public void setArtifactId(String artifactId) {
		this.artifactId = artifactId;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getRepository() {
		return repository;
	}

	public void setRepository(String repository) {
		this.repository = repository;
	}

	public String getTarget() {
		return target;
	}

	/*
	 * public void setTarget(String target) { this.target = target; }
	 */
	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
