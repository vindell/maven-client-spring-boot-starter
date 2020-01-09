/*
 * Copyright (c) 2018, hiwepy (https://github.com/hiwepy).
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ChoiceFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.spring.boot.MavenClientProperties;
import org.apache.maven.spring.boot.utils.ArtifactUtils;
import org.apache.maven.spring.boot.utils.RepositorySystemUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.AbstractArtifact;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.google.common.collect.Iterables;

/*
 * 	
 * 	<b>基于maven-aether-provider的Maven Client实现，不依赖于本机的Maven环境即可实现Maven组件相关操作：</b>
 * 	<p>支持操作：</p>
 * 	<p>1、解析构件信息</p>
 * 	<p>2、解析版本信息</p>
 * 	<p>3、解析依赖信息</p>
 * 	<p>4、从远程仓库下载构件</p>
 * 	<p>5、安装指定构件到本地仓库</p>
 * 	<p>6、发布本地构件到远程仓库</p>
 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
 */
public class MavenClientTemplate {

	private Logger log = LoggerFactory.getLogger(RepositorySystemUtils.class);
	private String DEFAULT_CONTENT_TYPE = "default";
	private List<RemoteRepository> remoteRepositories = new LinkedList<RemoteRepository>();
	private Map<String, RemoteRepository> remoteRepositoriesMap = new HashMap<String, RemoteRepository>();
	private final RepositorySystem repositorySystem;
	private MavenClientProperties properties;
	private final Authentication authentication;
	private MavenXpp3Reader modelReader = new MavenXpp3Reader();

	/**
	 * Create an instance using the provided properties.
	 * 
	 * @param mavenProperties the properties for the maven repositories, proxies, and
	 *                   authentication
	 */
	public MavenClientTemplate(MavenClientProperties mavenProperties) {
		this.properties = mavenProperties;
		Assert.notNull(properties, "MavenProperties must not be null");
		Assert.notNull(properties.getLocalRepository(), "Local repository path cannot be null");
		if (log.isDebugEnabled()) {
			log.debug("Local repository: " + properties.getLocalRepository());
			log.debug("Remote repositories: "
					+ StringUtils.collectionToCommaDelimitedString(properties.getRemoteRepositories().keySet()));
		}
		if (RepositorySystemUtils.isProxyEnabled(mavenProperties)
				&& RepositorySystemUtils.proxyHasCredentials(mavenProperties)) {
			final String username = this.properties.getProxy().getAuth().getUsername();
			final String password = this.properties.getProxy().getAuth().getPassword();
			this.authentication = RepositorySystemUtils.newAuthentication(username, password);
		} else {
			this.authentication = null;
		}
		File localRepository = new File(this.properties.getLocalRepository());
		if (!localRepository.exists()) {
			boolean created = localRepository.mkdirs();
			// May have been created by another thread after above check. Double check.
			Assert.isTrue(created || localRepository.exists(),
					"Unable to create directory for local repository: " + localRepository);
		}
		for (Map.Entry<String, MavenProperties.RemoteRepository> entry : this.properties.getRemoteRepositories()
				.entrySet()) {
			MavenProperties.RemoteRepository remoteRepository = entry.getValue();
			RemoteRepository.Builder remoteRepositoryBuilder = new RemoteRepository.Builder(entry.getKey(),
					DEFAULT_CONTENT_TYPE, remoteRepository.getUrl());
			// Update policies when set.
			if (remoteRepository.getPolicy() != null) {
				remoteRepositoryBuilder.setPolicy(new RepositoryPolicy(remoteRepository.getPolicy().isEnabled(),
						remoteRepository.getPolicy().getUpdatePolicy(),
						remoteRepository.getPolicy().getChecksumPolicy()));
			}
			if (remoteRepository.getReleasePolicy() != null) {
				remoteRepositoryBuilder
						.setReleasePolicy(new RepositoryPolicy(remoteRepository.getReleasePolicy().isEnabled(),
								remoteRepository.getReleasePolicy().getUpdatePolicy(),
								remoteRepository.getReleasePolicy().getChecksumPolicy()));
			}
			if (remoteRepository.getSnapshotPolicy() != null) {
				remoteRepositoryBuilder
						.setSnapshotPolicy(new RepositoryPolicy(remoteRepository.getSnapshotPolicy().isEnabled(),
								remoteRepository.getSnapshotPolicy().getUpdatePolicy(),
								remoteRepository.getSnapshotPolicy().getChecksumPolicy()));
			}
			if (RepositorySystemUtils.isProxyEnabled(mavenProperties)) {
				MavenProperties.Proxy proxyProperties = this.properties.getProxy();
				if (this.authentication != null) {
					remoteRepositoryBuilder.setProxy(new Proxy(proxyProperties.getProtocol(), proxyProperties.getHost(),
							proxyProperties.getPort(), this.authentication));
				} else {
					// if proxy does not require authentication
					remoteRepositoryBuilder.setProxy(new Proxy(proxyProperties.getProtocol(), proxyProperties.getHost(),
							proxyProperties.getPort()));
				}
			}
			if (RepositorySystemUtils.remoteRepositoryHasCredentials(remoteRepository)) {
				final String username = remoteRepository.getAuth().getUsername();
				final String password = remoteRepository.getAuth().getPassword();
				remoteRepositoryBuilder.setAuthentication(RepositorySystemUtils.newAuthentication(username, password));
			}

			RemoteRepository repository = remoteRepositoryBuilder.build();
			this.remoteRepositoriesMap.put(entry.getKey(), repository);
			this.remoteRepositories.add(repository);
		}
		this.repositorySystem = RepositorySystemUtils.newRepositorySystem();
	}

	/**
	 * get ArtifactResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param coordinates The artifact coordinates in the format
	 *                    {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>},
	 *                    must not be {@code null}.
	 * @return a {@link ArtifactResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public ArtifactResult artifact(String coordinates) {
		Assert.notNull(coordinates, "coordinates must not be null");
		return this.artifact(MavenResource.parse(coordinates, properties));
	}

	/**
	 * get ArtifactResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link ArtifactResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public ArtifactResult artifact(String groupId, String artifactId, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.artifact(resource);
	}

	/**
	 * get ArtifactResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link ArtifactResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public ArtifactResult artifact(String groupId, String artifactId, String classifier, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.artifact(resource);
	}

	/**
	 * get ArtifactResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param extension  The file extension of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link ArtifactResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public ArtifactResult artifact(String groupId, String artifactId, String classifier, String extension,
			String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).extension(extension).version(version).build();
		return this.artifact(resource);
	}

	/**
	 * get ArtifactResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link ArtifactResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public ArtifactResult artifact(MavenResource resource) {

		Assert.notNull(resource, "MavenResource must not be null");
		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem,
				properties, authentication);
		validateCoordinates(resource);

		try {

			ArtifactRequest request = new ArtifactRequest(ArtifactUtils.toJarArtifact(resource),
					this.remoteRepositories, JavaScopes.RUNTIME);

			return this.repositorySystem.resolveArtifact(session, request);

		} catch (ArtifactResolutionException e) {
			ChoiceFormat pluralizer = new ChoiceFormat(new double[] { 0d, 1d, ChoiceFormat.nextDouble(1d) },
					new String[] { "repositories: ", "repository: ", "repositories: " });
			MessageFormat messageFormat = new MessageFormat(
					"Failed to resolve MavenResource: {0}. Configured remote {1}: {2}");
			messageFormat.setFormat(1, pluralizer);
			String repos = properties.getRemoteRepositories().isEmpty() ? "none"
					: StringUtils.collectionToDelimitedString(properties.getRemoteRepositories().keySet(), ",", "[",
							"]");
			throw new IllegalStateException(
					messageFormat.format(new Object[] { resource, properties.getRemoteRepositories().size(), repos }),
					e);
		}
	}

	/**
	 * get MetadataResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param coordinates The artifact coordinates in the format
	 *                    {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>},
	 *                    must not be {@code null}.
	 * @return a {@link MetadataResult} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public List<ArtifactResult> artifacts(String coordinates) {
		return this.artifacts(MavenResource.parse(coordinates, properties));
	}

	/**
	 * get MetadataResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link MetadataResult} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public List<ArtifactResult> artifacts(String groupId, String artifactId, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.artifacts(resource);
	}

	/**
	 * get MetadataResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link MetadataResult} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public List<ArtifactResult> artifacts(String groupId, String artifactId, String classifier, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.artifacts(resource);
	}

	/**
	 * get MetadataResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param extension  The file extension of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link MetadataResult} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public List<ArtifactResult> artifacts(String groupId, String artifactId, String classifier, String extension,
			String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).extension(extension).version(version).build();
		return this.artifacts(resource);
	}

	/**
	 * Resolve an artifact and return its location in the local repository. Aether
	 * performs the normal Maven resolution process ensuring that the latest update
	 * is cached to the local repository. In addition, if the
	 * {@link MavenProperties#resolvePom} flag is <code>true</code>, the POM is also
	 * resolved and cached.
	 * 
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link MetadataResult} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public List<ArtifactResult> artifacts(MavenResource resource) {
		Assert.notNull(resource, "MavenResource must not be null");
		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem,
				this.properties, this.authentication);
		validateCoordinates(resource);
		try {

			List<ArtifactRequest> artifactRequests = new ArrayList<>(2);
			if (properties.isResolvePom()) {
				artifactRequests.add(new ArtifactRequest(ArtifactUtils.toPomArtifact(resource), this.remoteRepositories,
						JavaScopes.RUNTIME));
			}
			if (properties.isResolveJavadoc()) {
				artifactRequests.add(new ArtifactRequest(ArtifactUtils.toJavadocArtifact(resource),
						this.remoteRepositories, JavaScopes.COMPILE));
			}
			if (properties.isResolveSources()) {
				artifactRequests.add(new ArtifactRequest(ArtifactUtils.toSourcesArtifact(resource),
						this.remoteRepositories, JavaScopes.COMPILE));
			}
			artifactRequests.add(new ArtifactRequest(ArtifactUtils.toJarArtifact(resource), this.remoteRepositories,
					JavaScopes.RUNTIME));

			return this.repositorySystem.resolveArtifacts(session, artifactRequests);

		} catch (ArtifactResolutionException e) {

			ChoiceFormat pluralizer = new ChoiceFormat(new double[] { 0d, 1d, ChoiceFormat.nextDouble(1d) },
					new String[] { "repositories: ", "repository: ", "repositories: " });
			MessageFormat messageFormat = new MessageFormat(
					"Failed to resolve MavenResource: {0}. Configured remote {1}: {2}");
			messageFormat.setFormat(1, pluralizer);
			String repos = properties.getRemoteRepositories().isEmpty() ? "none"
					: StringUtils.collectionToDelimitedString(properties.getRemoteRepositories().keySet(), ",", "[",
							"]");
			throw new IllegalStateException(
					messageFormat.format(new Object[] { resource, properties.getRemoteRepositories().size(), repos }),
					e);
		}
	}

	/**
	 * get DependencyResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param coordinates The artifact coordinates in the format
	 *                    {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>},
	 *                    must not be {@code null}.
	 * @return a {@link DependencyResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public DependencyResult dependencies(String coordinates) {
		Assert.notNull(coordinates, "coordinates must not be null");
		return this.dependencies(MavenResource.parse(coordinates, properties));
	}

	/**
	 * get DependencyResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link DependencyResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public DependencyResult dependencies(String groupId, String artifactId, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.dependencies(resource);
	}

	/**
	 * get DependencyResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link DependencyResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public DependencyResult dependencies(String groupId, String artifactId, String classifier, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.dependencies(resource);
	}

	/**
	 * get DependencyResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param extension  The file extension of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link DependencyResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public DependencyResult dependencies(String groupId, String artifactId, String classifier, String extension,
			String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).extension(extension).version(version).build();
		return this.dependencies(resource);
	}

	/**
	 * get DependencyResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link DependencyResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public DependencyResult dependencies(MavenResource resource) {

		Assert.notNull(resource, "MavenResource must not be null");
		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem,
				properties, authentication);
		validateCoordinates(resource);

		try {

			CollectRequest collectRequest = new CollectRequest();
			collectRequest.setRepositories(this.remoteRepositories);
			collectRequest.setRootArtifact(ArtifactUtils.toJarArtifact(resource));

			DependencyRequest request = new DependencyRequest();
			request.setCollectRequest(collectRequest);

			return this.repositorySystem.resolveDependencies(session, request);

		} catch (DependencyResolutionException e) {
			ChoiceFormat pluralizer = new ChoiceFormat(new double[] { 0d, 1d, ChoiceFormat.nextDouble(1d) },
					new String[] { "repositories: ", "repository: ", "repositories: " });
			MessageFormat messageFormat = new MessageFormat(
					"Failed to resolve MavenResource: {0}. Configured remote {1}: {2}");
			messageFormat.setFormat(1, pluralizer);
			String repos = properties.getRemoteRepositories().isEmpty() ? "none"
					: StringUtils.collectionToDelimitedString(properties.getRemoteRepositories().keySet(), ",", "[",
							"]");
			throw new IllegalStateException(
					messageFormat.format(new Object[] { resource, properties.getRemoteRepositories().size(), repos }),
					e);
		}
	}

	/**
	 * get  MetadataResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param coordinates The artifact coordinates in the format
	 *                    {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>},
	 *                    must not be {@code null}.
	 * @return a {@link MetadataResult} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public List<MetadataResult> metadata(String coordinates) {
		Assert.notNull(coordinates, "coordinates must not be null");
		return this.metadata(MavenResource.parse(coordinates, properties));
	}

	/**
	 * get  MetadataResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link MetadataResult} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public List<MetadataResult> metadata(String groupId, String artifactId, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.metadata(resource);
	}

	/**
	 * get  MetadataResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link MetadataResult} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public List<MetadataResult> metadata(String groupId, String artifactId, String classifier, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.metadata(resource);
	}

	/**
	 * get  MetadataResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param extension  The file extension of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link MetadataResult} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public List<MetadataResult> metadata(String groupId, String artifactId, String classifier, String extension,
			String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).extension(extension).version(version).build();
		return this.metadata(resource);
	}

	/**
	 * get MetadataResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link MetadataResult} representing the resolved artifact in
	 *         the local repository
	 */
	public List<MetadataResult> metadata(MavenResource resource) {

		Assert.notNull(resource, "MavenResource must not be null");
		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem,
				properties, authentication);
		validateCoordinates(resource);

		List<MetadataRequest> requests = new ArrayList<>(this.remoteRepositories.size());
		for (RemoteRepository repository : this.remoteRepositories) {
			requests.add(new MetadataRequest().setDeleteLocalCopyIfMissing(properties.isDeleteLocalCopyIfMissing())
					.setFavorLocalRepository(properties.isFavorLocalRepository()).setRepository(repository));
		}

		return this.repositorySystem.resolveMetadata(session, requests);
	}

	public Model resolve(File file) throws XmlPullParserException, IOException {
		try (ZipFile zipFile = new ZipFile(file)) {
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				// System.out.println(entry.getName());
				if (entry.getName().endsWith("pom.xml")) {
					InputStream input = zipFile.getInputStream(entry);
					Model model = modelReader.read(new InputStreamReader(input));
					return model;
				}
			}
		}
		throw new IOException("Not a maven project, unable to parse version information.");
	}

	/**
	 * Resolve an artifact and return its location in the local repository. Aether
	 * performs the normal Maven resolution process ensuring that the latest update
	 * is cached to the local repository. In addition, if the
	 * {@link MavenProperties#resolvePom} flag is <code>true</code>, the POM is also
	 * resolved and cached.
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param coordinates The artifact coordinates in the format
	 *                    {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>},
	 *                    must not be {@code null}.
	 * @return a {@link Resource} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public Resource resource(String coordinates) {
		return this.resource(MavenResource.parse(coordinates, properties));
	}

	/**
	 * Resolve an artifact and return its location in the local repository. Aether
	 * performs the normal Maven resolution process ensuring that the latest update
	 * is cached to the local repository. In addition, if the
	 * {@link MavenProperties#resolvePom} flag is <code>true</code>, the POM is also
	 * resolved and cached.
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link Resource} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public Resource resource(String groupId, String artifactId, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.resource(resource);
	}

	/**
	 * Resolve an artifact and return its location in the local repository. Aether
	 * performs the normal Maven resolution process ensuring that the latest update
	 * is cached to the local repository. In addition, if the
	 * {@link MavenProperties#resolvePom} flag is <code>true</code>, the POM is also
	 * resolved and cached.
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link Resource} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public Resource resource(String groupId, String artifactId, String classifier, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.resource(resource);
	}

	/**
	 * Resolve an artifact and return its location in the local repository. Aether
	 * performs the normal Maven resolution process ensuring that the latest update
	 * is cached to the local repository. In addition, if the
	 * {@link MavenProperties#resolvePom} flag is <code>true</code>, the POM is also
	 * resolved and cached.
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param extension  The file extension of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link Resource} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public Resource resource(String groupId, String artifactId, String classifier, String extension, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).extension(extension).version(version).build();
		return this.resource(resource);
	}

	/**
	 * Resolve an artifact and return its location in the local repository. Aether
	 * performs the normal Maven resolution process ensuring that the latest update
	 * is cached to the local repository. In addition, if the
	 * {@link MavenProperties#resolvePom} flag is <code>true</code>, the POM is also
	 * resolved and cached.
	 * 
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link FileSystemResource} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public Resource resource(MavenResource resource) {
		Assert.notNull(resource, "MavenResource must not be null");
		validateCoordinates(resource);
		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem,
				this.properties, this.authentication);
		ArtifactResult resolvedArtifact;
		try {

			List<ArtifactRequest> artifactRequests = new ArrayList<>(2);
			if (properties.isResolvePom()) {
				artifactRequests.add(new ArtifactRequest(ArtifactUtils.toPomArtifact(resource), this.remoteRepositories,
						JavaScopes.RUNTIME));
			}
			if (properties.isResolveJavadoc()) {
				artifactRequests.add(new ArtifactRequest(ArtifactUtils.toJavadocArtifact(resource),
						this.remoteRepositories, JavaScopes.COMPILE));
			}
			if (properties.isResolveSources()) {
				artifactRequests.add(new ArtifactRequest(ArtifactUtils.toSourcesArtifact(resource),
						this.remoteRepositories, JavaScopes.COMPILE));
			}
			artifactRequests.add(new ArtifactRequest(ArtifactUtils.toJarArtifact(resource), this.remoteRepositories,
					JavaScopes.RUNTIME));

			List<ArtifactResult> results = this.repositorySystem.resolveArtifacts(session, artifactRequests);
			resolvedArtifact = results.get(results.size() - 1);
		} catch (ArtifactResolutionException e) {

			ChoiceFormat pluralizer = new ChoiceFormat(new double[] { 0d, 1d, ChoiceFormat.nextDouble(1d) },
					new String[] { "repositories: ", "repository: ", "repositories: " });
			MessageFormat messageFormat = new MessageFormat(
					"Failed to resolve MavenResource: {0}. Configured remote {1}: {2}");
			messageFormat.setFormat(1, pluralizer);
			String repos = properties.getRemoteRepositories().isEmpty() ? "none"
					: StringUtils.collectionToDelimitedString(properties.getRemoteRepositories().keySet(), ",", "[",
							"]");
			throw new IllegalStateException(
					messageFormat.format(new Object[] { resource, properties.getRemoteRepositories().size(), repos }),
					e);
		}
		return ArtifactUtils.toResource(resolvedArtifact);
	}

	private void validateCoordinates(MavenResource resource) {
		Assert.hasText(resource.getGroupId(), "groupId must not be blank.");
		Assert.hasText(resource.getArtifactId(), "artifactId must not be blank.");
		Assert.hasText(resource.getExtension(), "extension must not be blank.");
		Assert.hasText(resource.getVersion(), "version must not be blank.");
	}

	/**
	 * get VersionResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param coordinates The artifact coordinates in the format
	 *                    {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>},
	 *                    must not be {@code null}.
	 * @return a {@link VersionResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public VersionResult version(String coordinates) {
		Assert.notNull(coordinates, "coordinates must not be null");
		return this.version(MavenResource.parse(coordinates, properties));
	}

	/**
	 * get VersionResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link VersionResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public VersionResult version(String groupId, String artifactId, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.version(resource);
	}

	/**
	 * get VersionResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link VersionResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public VersionResult version(String groupId, String artifactId, String classifier, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.version(resource);
	}

	/**
	 * get VersionResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param extension  The file extension of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link VersionResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public VersionResult version(String groupId, String artifactId, String classifier, String extension,
			String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).extension(extension).version(version).build();
		return this.version(resource);
	}

	/**
	 * get VersionResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link VersionResult} representing the resolved artifact in the
	 *         local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public VersionResult version(MavenResource resource) {

		Assert.notNull(resource, "MavenResource must not be null");
		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem,
				properties, authentication);
		validateCoordinates(resource);

		try {

			VersionRequest request = new VersionRequest(ArtifactUtils.toJarArtifact(resource), this.remoteRepositories,
					JavaScopes.RUNTIME);

			return this.repositorySystem.resolveVersion(session, request);

		} catch (VersionResolutionException e) {
			ChoiceFormat pluralizer = new ChoiceFormat(new double[] { 0d, 1d, ChoiceFormat.nextDouble(1d) },
					new String[] { "repositories: ", "repository: ", "repositories: " });
			MessageFormat messageFormat = new MessageFormat(
					"Failed to resolve MavenResource: {0}. Configured remote {1}: {2}");
			messageFormat.setFormat(1, pluralizer);
			String repos = properties.getRemoteRepositories().isEmpty() ? "none"
					: StringUtils.collectionToDelimitedString(properties.getRemoteRepositories().keySet(), ",", "[",
							"]");
			throw new IllegalStateException(
					messageFormat.format(new Object[] { resource, properties.getRemoteRepositories().size(), repos }),
					e);
		}
	}

	/**
	 * get VersionRangeResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param coordinates The artifact coordinates in the format
	 *                    {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>},
	 *                    must not be {@code null}.
	 * @return a {@link VersionRangeResult} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public VersionRangeResult versionRange(String coordinates) {
		Assert.notNull(coordinates, "coordinates must not be null");
		return this.versionRange(MavenResource.parse(coordinates, properties));
	}

	/**
	 * get VersionRangeResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link VersionRangeResult} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public VersionRangeResult versionRange(String groupId, String artifactId, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.versionRange(resource);
	}

	/**
	 * get VersionRangeResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link VersionRangeResult} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public VersionRangeResult versionRange(String groupId, String artifactId, String classifier, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.versionRange(resource);
	}

	/**
	 * get VersionRangeResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param extension  The file extension of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link VersionRangeResult} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public VersionRangeResult versionRange(String groupId, String artifactId, String classifier, String extension,
			String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).extension(extension).version(version).build();
		return this.versionRange(resource);
	}

	/**
	 * get VersionRangeResult
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link VersionRangeResult} representing the resolved artifact in
	 *         the local repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public VersionRangeResult versionRange(MavenResource resource) {

		Assert.notNull(resource, "MavenResource must not be null");
		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem,
				properties, authentication);
		validateCoordinates(resource);

		try {

			AbstractArtifact artifact = new DefaultArtifact(
					resource.getGroupId() + ":" + resource.getArtifactId() + ":[0,)");

			VersionRangeRequest rangeRequest = new VersionRangeRequest(artifact, this.remoteRepositories,
					JavaScopes.RUNTIME);

			return this.repositorySystem.resolveVersionRange(session, rangeRequest);

		} catch (VersionRangeResolutionException e) {
			ChoiceFormat pluralizer = new ChoiceFormat(new double[] { 0d, 1d, ChoiceFormat.nextDouble(1d) },
					new String[] { "repositories: ", "repository: ", "repositories: " });
			MessageFormat messageFormat = new MessageFormat(
					"Failed to resolve MavenResource: {0}. Configured remote {1}: {2}");
			messageFormat.setFormat(1, pluralizer);
			String repos = properties.getRemoteRepositories().isEmpty() ? "none"
					: StringUtils.collectionToDelimitedString(properties.getRemoteRepositories().keySet(), ",", "[",
							"]");
			throw new IllegalStateException(
					messageFormat.format(new Object[] { resource, properties.getRemoteRepositories().size(), repos }),
					e);
		}
	}

	/**
	 * get last version
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link Version} representing the resolved artifact in the local
	 *         repository
	 * @throws IllegalStateException if the artifact does not exist or the
	 *                               resolution fails
	 */
	public Version lastVersion(MavenResource resource) {

		Assert.notNull(resource, "MavenResource must not be null");
		validateCoordinates(resource);

		VersionRangeResult rangeResult = this.versionRange(resource);

		List<Version> versions = rangeResult.getVersions();

		Collections.sort(versions);

		return Iterables.getLast(versions);
	}

	/**
	 * install file to maven repository
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param path        The file to install.
	 * @param coordinates The artifact coordinates in the format
	 *                    {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>},
	 *                    must not be {@code null}.
	 * @return a {@link InstallResult} representing the installed artifact in the
	 *         local repository
	 * @throws InstallationException if the artifact does not exist or the install
	 *                               fails
	 */
	public InstallResult install(File path, String coordinates) throws InstallationException {
		Assert.notNull(coordinates, "coordinates must not be null");
		return this.install(path, MavenResource.parse(coordinates, properties));
	}

	/**
	 * install file to maven repository
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param path       The file to install.
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link InstallResult} representing the installed artifact in the
	 *         local repository
	 * @throws InstallationException if the artifact does not exist or the install
	 *                               fails
	 */
	public InstallResult install(File path, String groupId, String artifactId, String version)
			throws InstallationException {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.install(path, resource);
	}

	/**
	 * install file to maven repository
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param path       The file to install.
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link InstallResult} representing the installed artifact in the
	 *         local repository
	 * @throws InstallationException if the artifact does not exist or the install
	 *                               fails
	 */
	public InstallResult install(File path, String groupId, String artifactId, String classifier, String version)
			throws InstallationException {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.install(path, resource);
	}

	/**
	 * install file to maven repository
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param path       The file to install.
	 * @param groupId    The group identifier of the artifact, may be {@code null}.
	 * @param artifactId The artifact identifier of the artifact, may be
	 *                   {@code null}.
	 * @param classifier The classifier of the artifact, may be {@code null}.
	 * @param extension  The file extension of the artifact, may be {@code null}.
	 * @param version    The version of the artifact, may be {@code null}.
	 * @return a {@link InstallResult} representing the installed artifact in the
	 *         local repository
	 * @throws InstallationException if the artifact does not exist or the install
	 *                               fails
	 */
	public InstallResult install(File path, String groupId, String artifactId, String classifier, String extension,
			String version) throws InstallationException {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).extension(extension).version(version).build();
		return this.install(path, resource);
	}

	/**
	 * install file to maven repository
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param path     The file to install.
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link InstallResult} representing the installed artifact in the
	 *         local repository
	 * @throws InstallationException if the artifact does not exist or the install
	 *                               fails
	 */
	public InstallResult install(File path, MavenResource resource) throws InstallationException {

		Artifact artifact = ArtifactUtils.toJarArtifact(resource);
		artifact.setFile(path);

		return this.install(artifact);
	}

	/**
	 * install artifacts to maven repository
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param artifacts The artifact to install.
	 * @return a {@link InstallResult} representing the installed artifact in the
	 *         local repository
	 * @throws InstallationException if the artifact does not exist or the install
	 *                               fails
	 */
	public InstallResult install(Artifact... artifacts) throws InstallationException {

		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem,
				properties, authentication);

		InstallRequest request = new InstallRequest();
		request.setArtifacts(Arrays.asList(artifacts));

		return this.repositorySystem.install(session, request);

	}

	/**
	 * install file to maven repository
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param path         The file to install.
	 * @param coordinates  The artifact coordinates in the format
	 *                     {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>},
	 *                     must not be {@code null}.
	 * @param repositoryId The remote repository id.
	 * @return a {@link DeployResult} representing the deployed artifact in the
	 *         remote repository
	 * @throws DeploymentException if the artifact does not exist or the deploy
	 *                             fails
	 */
	public DeployResult deploy(File path, String coordinates, String repositoryId) throws DeploymentException {
		Assert.notNull(coordinates, "coordinates must not be null");

		MavenResource resource = MavenResource.parse(coordinates, properties);

		Artifact artifact = ArtifactUtils.toJarArtifact(resource);
		artifact.setFile(path);

		return this.deploy(this.remoteRepositoriesMap.get(repositoryId), artifact);

	}

	/**
	 * install file to maven repository
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param path         The file to install.
	 * @param groupId      The group identifier of the artifact, may be
	 *                     {@code null}.
	 * @param artifactId   The artifact identifier of the artifact, may be
	 *                     {@code null}.
	 * @param version      The version of the artifact, may be {@code null}.
	 * @param repositoryId The remote repository id.
	 * @return a {@link DeployResult} representing the deployed artifact in the
	 *         remote repository
	 * @throws DeploymentException if the artifact does not exist or the deploy
	 *                             fails
	 */
	public DeployResult deploy(File path, String groupId, String artifactId, String version, String repositoryId)
			throws DeploymentException {
		MavenResource resource = new MavenResource.Builder().groupId(groupId)
				.artifactId(artifactId).version(version)
				.build();
		Artifact artifact = ArtifactUtils.toJarArtifact(resource);
		artifact.setFile(path);

		return this.deploy(this.remoteRepositoriesMap.get(repositoryId), artifact);
	}

	/**
	 * install file to maven repository
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param path         The file to install.
	 * @param groupId      The group identifier of the artifact, may be
	 *                     {@code null}.
	 * @param artifactId   The artifact identifier of the artifact, may be
	 *                     {@code null}.
	 * @param classifier   The classifier of the artifact, may be {@code null}.
	 * @param version      The version of the artifact, may be {@code null}.
	 * @param repositoryId The remote repository id.
	 * @return a {@link DeployResult} representing the deployed artifact in the
	 *         remote repository
	 * @throws DeploymentException if the artifact does not exist or the deploy
	 *                             fails
	 */
	public DeployResult deploy(File path, String groupId, String artifactId, String classifier, String version,
			String repositoryId) throws DeploymentException {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		
		Artifact artifact = ArtifactUtils.toJarArtifact(resource);
		artifact.setFile(path);

		return this.deploy(this.remoteRepositoriesMap.get(repositoryId), artifact);
	}

	/**
	 * install file to maven repository
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param path         The file to install.
	 * @param groupId      The group identifier of the artifact, may be
	 *                     {@code null}.
	 * @param artifactId   The artifact identifier of the artifact, may be
	 *                     {@code null}.
	 * @param classifier   The classifier of the artifact, may be {@code null}.
	 * @param extension    The file extension of the artifact, may be {@code null}.
	 * @param version      The version of the artifact, may be {@code null}.
	 * @param repositoryId The remote repository id.
	 * @return a {@link DeployResult} representing the deployed artifact in the
	 *         remote repository
	 * @throws DeploymentException if the artifact does not exist or the deploy
	 *                             fails
	 */
	public DeployResult deploy(File path, String groupId, String artifactId, String classifier, String extension,
			String version, String repositoryId) throws DeploymentException {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).extension(extension).version(version).build();

		Artifact artifact = ArtifactUtils.toJarArtifact(resource);
		artifact.setFile(path);

		return this.deploy(this.remoteRepositoriesMap.get(repositoryId), artifact);
	}

	/**
	 * install artifacts to maven repository
	 * 
	 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
	 * @param repository The repository to deploy.
	 * @param artifacts  The artifact to install.
	 * @return a {@link DeployResult} representing the deployed artifact in the
	 *         local repository
	 * @throws DeploymentException if the artifact does not exist or the deploy
	 *                             fails
	 */
	public DeployResult deploy(RemoteRepository repository, Artifact... artifacts) throws DeploymentException {

		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem,
				properties, authentication);

		DeployRequest request = new DeployRequest();
		request.setRepository(repository);
		request.setArtifacts(Arrays.asList(artifacts));
		
		return this.repositorySystem.deploy(session, request);

	}

}
