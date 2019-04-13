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
package org.apache.maven.spring.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;

/**
 * Maven Settings
 * @author ： <a href="https://github.com/vindell">vindell</a>
 */
@ConfigurationProperties(MavenClientProperties.PREFIX)
public class MavenClientProperties extends MavenProperties {

	public static final String PREFIX = "maven.settings";

	private boolean deleteLocalCopyIfMissing;

	private boolean favorLocalRepository;
	
	/**
	 * In addition to resolving the JAR artifact, if true, resolve the javadoc artifact.
	 * This is consistent with the way that Maven resolves artifacts.
	 */
	private boolean resolveJavadoc;
	
	/**
	 * In addition to resolving the JAR artifact, if true, resolve the sources artifact.
	 * This is consistent with the way that Maven resolves artifacts.
	 */
	private boolean resolveSources;
	
	/**
	 * Indicates whether the locally cached copy of the metadata should be removed
	 * if the corresponding file does not exist (any more) in the remote repository.
	 * 
	 * @return {@code true} if locally cached metadata should be deleted if no
	 *         corresponding remote file exists, {@code false} to keep the local
	 *         copy.
	 */
	public boolean isDeleteLocalCopyIfMissing() {
		return deleteLocalCopyIfMissing;
	}

	/**
	 * Controls whether the locally cached copy of the metadata should be removed if
	 * the corresponding file does not exist (any more) in the remote repository.
	 * 
	 * @param deleteLocalCopyIfMissing {@code true} if locally cached metadata
	 *                                 should be deleted if no corresponding remote
	 *                                 file exists, {@code false} to keep the local
	 *                                 copy.
	 * @return This request for chaining, never {@code null}.
	 */
	public void setDeleteLocalCopyIfMissing(boolean deleteLocalCopyIfMissing) {
		this.deleteLocalCopyIfMissing = deleteLocalCopyIfMissing;
	}

	/**
	 * Indicates whether the metadata resolution should be suppressed if the
	 * corresponding metadata of the local repository is up-to-date according to the
	 * update policy of the remote repository. In this case, the metadata resolution
	 * will even be suppressed if no local copy of the remote metadata exists yet.
	 * 
	 * @return {@code true} to suppress resolution of remote metadata if the
	 *         corresponding metadata of the local repository is up-to-date,
	 *         {@code false} to resolve the remote metadata normally according to
	 *         the update policy.
	 */
	public boolean isFavorLocalRepository() {
		return favorLocalRepository;
	}

	/**
	 * Controls resolution of remote metadata when already corresponding metadata of
	 * the local repository exists. In cases where the local repository's metadata
	 * is sufficient and going to be preferred, resolution of the remote metadata
	 * can be suppressed to avoid unnecessary network access.
	 * 
	 * @param favorLocalRepository {@code true} to suppress resolution of remote
	 *                             metadata if the corresponding metadata of the
	 *                             local repository is up-to-date, {@code false} to
	 *                             resolve the remote metadata normally according to
	 *                             the update policy.
	 * @return This request for chaining, never {@code null}.
	 */
	public void setFavorLocalRepository(boolean favorLocalRepository) {
		this.favorLocalRepository = favorLocalRepository;
	}

	public boolean isResolveJavadoc() {
		return resolveJavadoc;
	}

	public void setResolveJavadoc(boolean resolveJavadoc) {
		this.resolveJavadoc = resolveJavadoc;
	}

	public boolean isResolveSources() {
		return resolveSources;
	}

	public void setResolveSources(boolean resolveSources) {
		this.resolveSources = resolveSources;
	}

}
