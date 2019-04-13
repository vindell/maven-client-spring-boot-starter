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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ChoiceFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.spring.boot.MavenClientProperties;
import org.apache.maven.spring.boot.utils.RepositorySystemUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.AbstractArtifact;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 基于Maven Invoker的Maven build实现，依赖于本机环境中的Maven环境
 * 
 * @author ： <a href="https://github.com/vindell">vindell</a>
 */
public class MavenClientTemplate {
	
	private Logger log = LoggerFactory.getLogger(RepositorySystemUtils.class);
	private String DEFAULT_CONTENT_TYPE = "default";
	private List<RemoteRepository> remoteRepositories = new LinkedList<RemoteRepository>();
	private final RepositorySystem repositorySystem;
	private MavenClientProperties properties;
	private final Authentication authentication;
	private MavenXpp3Reader modelReader = new MavenXpp3Reader();
	
	/**
	 * Create an instance using the provided properties.
	 * @param properties the properties for the maven repositories, proxies, and authentication
	 */
	public MavenClientTemplate(MavenClientProperties mavenProperties) {
		this.properties = mavenProperties;
		Assert.notNull(properties, "MavenProperties must not be null");
		Assert.notNull(properties.getLocalRepository(), "Local repository path cannot be null");
		if (log.isDebugEnabled()) {
			log.debug("Local repository: " + properties.getLocalRepository());
			log.debug("Remote repositories: " +
					StringUtils.collectionToCommaDelimitedString(properties.getRemoteRepositories().keySet()));
		}
		if (RepositorySystemUtils.isProxyEnabled(mavenProperties) && RepositorySystemUtils.proxyHasCredentials(mavenProperties)) {
			final String username = this.properties.getProxy().getAuth().getUsername();
			final String password = this.properties.getProxy().getAuth().getPassword();
			this.authentication = RepositorySystemUtils.newAuthentication(username, password);
		}
		else {
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
			RemoteRepository.Builder remoteRepositoryBuilder = new RemoteRepository.Builder(
					entry.getKey(), DEFAULT_CONTENT_TYPE, remoteRepository.getUrl());
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
					remoteRepositoryBuilder.setProxy(new Proxy(
							proxyProperties.getProtocol(),
							proxyProperties.getHost(),
							proxyProperties.getPort(),
							this.authentication));
				}
				else {
					// if proxy does not require authentication
					remoteRepositoryBuilder.setProxy(new Proxy(
							proxyProperties.getProtocol(),
							proxyProperties.getHost(),
							proxyProperties.getPort()));
				}
			}
			if (RepositorySystemUtils.remoteRepositoryHasCredentials(remoteRepository)) {
				final String username = remoteRepository.getAuth().getUsername();
				final String password = remoteRepository.getAuth().getPassword();
				remoteRepositoryBuilder.setAuthentication(RepositorySystemUtils.newAuthentication(username, password));
			}
			this.remoteRepositories.add(remoteRepositoryBuilder.build());
		}
		this.repositorySystem = RepositorySystemUtils.newRepositorySystem();
	}
	
	public ArtifactResult artifact(String coordinates) {
		Assert.notNull(coordinates, "coordinates must not be null");
		return this.artifact(MavenResource.parse(coordinates, properties));
	}

	public ArtifactResult artifact(String groupId, String artifactId, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.artifact(resource);
	}

	public ArtifactResult artifact(String groupId, String artifactId, String classifier, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.artifact(resource);
	}

	public ArtifactResult artifact(String groupId, String artifactId, String classifier, String version, String extension) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).extension(extension).build();
		return this.artifact(resource);
	}
	 
	
	/**
	 * get ArtifactResult
	 * @author 		： <a href="https://github.com/vindell">vindell</a>
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link ArtifactResult} representing the resolved artifact in the local repository
	 * @throws IllegalStateException if the artifact does not exist or the resolution fails
	 */
	public ArtifactResult artifact(MavenResource resource) {
		
		Assert.notNull(resource, "MavenResource must not be null");
		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem,
				properties, authentication);
		validateCoordinates(resource);
		
		try {
			
			ArtifactRequest request = new ArtifactRequest(toJarArtifact(resource), this.remoteRepositories,
					JavaScopes.RUNTIME);
			
			return this.repositorySystem.resolveArtifact(session, request);
			
		} catch (ArtifactResolutionException e) {
			ChoiceFormat pluralizer = new ChoiceFormat(
					new double[] { 0d, 1d, ChoiceFormat.nextDouble(1d) },
					new String[] { "repositories: ", "repository: ", "repositories: " });
			MessageFormat messageFormat = new MessageFormat(
					"Failed to resolve MavenResource: {0}. Configured remote {1}: {2}");
			messageFormat.setFormat(1, pluralizer);
			String repos = properties.getRemoteRepositories().isEmpty()
					? "none"
					: StringUtils.collectionToDelimitedString(properties.getRemoteRepositories().keySet(), ",", "[", "]");
			throw new IllegalStateException(
					messageFormat.format(new Object[] { resource, properties.getRemoteRepositories().size(), repos }),
					e);
		}
	}
	
	public List<ArtifactResult> artifacts(String coordinates) {
		return this.artifacts(MavenResource.parse(coordinates, properties));
	}

	public List<ArtifactResult> artifacts(String groupId, String artifactId, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.artifacts(resource);
	}

	public List<ArtifactResult> artifacts(String groupId, String artifactId, String classifier, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.artifacts(resource);
	}

	public List<ArtifactResult> artifacts(String groupId, String artifactId, String classifier, String version, String extension) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).extension(extension).build();
		return this.artifacts(resource);
	}
	
	/**
	 * Resolve an artifact and return its location in the local repository. Aether performs the normal
	 * Maven resolution process ensuring that the latest update is cached to the local repository.
	 * In addition, if the {@link MavenProperties#resolvePom} flag is <code>true</code>,
	 * the POM is also resolved and cached.
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link List<ArtifactResult>} representing the resolved artifact in the local repository
	 * @throws IllegalStateException if the artifact does not exist or the resolution fails
	 */
	public List<ArtifactResult> artifacts(MavenResource resource) {
		Assert.notNull(resource, "MavenResource must not be null");
		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem, this.properties, this.authentication);
		validateCoordinates(resource);
		try {
			
			
			List<ArtifactRequest> artifactRequests = new ArrayList<>(2);
			if (properties.isResolvePom()) {
				artifactRequests.add(new ArtifactRequest(toPomArtifact(resource),
						this.remoteRepositories,
						JavaScopes.RUNTIME));
			}
			artifactRequests.add(new ArtifactRequest(toJarArtifact(resource),
					this.remoteRepositories,
					JavaScopes.RUNTIME));

			return this.repositorySystem.resolveArtifacts(session, artifactRequests);
			
		}
		catch (ArtifactResolutionException e) {

			ChoiceFormat pluralizer = new ChoiceFormat(
					new double[] { 0d, 1d, ChoiceFormat.nextDouble(1d) },
					new String[] { "repositories: ", "repository: ", "repositories: " });
			MessageFormat messageFormat = new MessageFormat(
					"Failed to resolve MavenResource: {0}. Configured remote {1}: {2}");
			messageFormat.setFormat(1, pluralizer);
			String repos = properties.getRemoteRepositories().isEmpty()
					? "none"
					: StringUtils.collectionToDelimitedString(properties.getRemoteRepositories().keySet(), ",", "[", "]");
			throw new IllegalStateException(
					messageFormat.format(new Object[] { resource, properties.getRemoteRepositories().size(), repos }),
					e);
		}
	}
	
	public DependencyResult dependencies(String coordinates) {
		Assert.notNull(coordinates, "coordinates must not be null");
		return this.dependencies(MavenResource.parse(coordinates, properties));
	}

	public DependencyResult dependencies(String groupId, String artifactId, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.dependencies(resource);
	}

	public DependencyResult dependencies(String groupId, String artifactId, String classifier, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.dependencies(resource);
	}

	public DependencyResult dependencies(String groupId, String artifactId, String classifier, String version, String extension) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).extension(extension).build();
		return this.dependencies(resource);
	}
	 
	
	/**
	 * get DependencyResult
	 * @author 		： <a href="https://github.com/vindell">vindell</a>
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link DependencyResult} representing the resolved artifact in the local repository
	 * @throws IllegalStateException if the artifact does not exist or the resolution fails
	 */
	public DependencyResult dependencies(MavenResource resource) {
		
		Assert.notNull(resource, "MavenResource must not be null");
		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem,
				properties, authentication);
		validateCoordinates(resource);
		
		try {
			
			CollectRequest collectRequest = new CollectRequest();
			collectRequest.setRepositories(this.remoteRepositories);
			collectRequest.setRootArtifact(toJarArtifact(resource));
					
			DependencyRequest request = new DependencyRequest();
			request.setCollectRequest(collectRequest);
			
			return this.repositorySystem.resolveDependencies(session, request);
			
		} catch (DependencyResolutionException e) {
			ChoiceFormat pluralizer = new ChoiceFormat(
					new double[] { 0d, 1d, ChoiceFormat.nextDouble(1d) },
					new String[] { "repositories: ", "repository: ", "repositories: " });
			MessageFormat messageFormat = new MessageFormat(
					"Failed to resolve MavenResource: {0}. Configured remote {1}: {2}");
			messageFormat.setFormat(1, pluralizer);
			String repos = properties.getRemoteRepositories().isEmpty()
					? "none"
					: StringUtils.collectionToDelimitedString(properties.getRemoteRepositories().keySet(), ",", "[", "]");
			throw new IllegalStateException(
					messageFormat.format(new Object[] { resource, properties.getRemoteRepositories().size(), repos }),
					e);
		}
	}
	
	public List<MetadataResult> metadata(String coordinates) {
		Assert.notNull(coordinates, "coordinates must not be null");
		return this.metadata(MavenResource.parse(coordinates, properties));
	}

	public List<MetadataResult> metadata(String groupId, String artifactId, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.metadata(resource);
	}

	public List<MetadataResult> metadata(String groupId, String artifactId, String classifier, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.metadata(resource);
	}

	public List<MetadataResult> metadata(String groupId, String artifactId, String classifier, String version, String extension) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).extension(extension).build();
		return this.metadata(resource);
	}
	 
	
	/**
	 * get List<MetadataResult>
	 * @author 		： <a href="https://github.com/vindell">vindell</a>
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link List<MetadataResult>} representing the resolved artifact in the local repository
	 */
	public List<MetadataResult> metadata(MavenResource resource) {
		
		Assert.notNull(resource, "MavenResource must not be null");
		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem,
				properties, authentication);
		validateCoordinates(resource);

		List<MetadataRequest> requests = new ArrayList<>(this.remoteRepositories.size());
		for (RemoteRepository repository : this.remoteRepositories) {
			requests.add(new MetadataRequest()
					.setDeleteLocalCopyIfMissing(properties.isDeleteLocalCopyIfMissing())
					.setFavorLocalRepository(properties.isFavorLocalRepository())
					.setRepository(repository));
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
	
	public Resource resource(String coordinates) {
		return this.resource(MavenResource.parse(coordinates, properties));
	}

	public Resource resource(String groupId, String artifactId, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.resource(resource);
	}

	public Resource resource(String groupId, String artifactId, String classifier, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.resource(resource);
	}

	public Resource resource(String groupId, String artifactId, String classifier, String version, String extension) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).extension(extension).build();
		return this.resource(resource);
	}
	
	/**
	 * Resolve an artifact and return its location in the local repository. Aether performs the normal
	 * Maven resolution process ensuring that the latest update is cached to the local repository.
	 * In addition, if the {@link MavenProperties#resolvePom} flag is <code>true</code>,
	 * the POM is also resolved and cached.
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link FileSystemResource} representing the resolved artifact in the local repository
	 * @throws IllegalStateException if the artifact does not exist or the resolution fails
	 */
	public Resource resource(MavenResource resource) {
		Assert.notNull(resource, "MavenResource must not be null");
		validateCoordinates(resource);
		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem, this.properties, this.authentication);
		ArtifactResult resolvedArtifact;
		try {
			
			List<ArtifactRequest> artifactRequests = new ArrayList<>(2);
			if (properties.isResolvePom()) {
				artifactRequests.add(new ArtifactRequest(toPomArtifact(resource),
						this.remoteRepositories,
						JavaScopes.RUNTIME));
			}
			artifactRequests.add(new ArtifactRequest(toJarArtifact(resource),
					this.remoteRepositories,
					JavaScopes.RUNTIME));

			List<ArtifactResult> results = this.repositorySystem.resolveArtifacts(session, artifactRequests);
			resolvedArtifact = results.get(results.size() - 1);
		}
		catch (ArtifactResolutionException e) {

			ChoiceFormat pluralizer = new ChoiceFormat(
					new double[] { 0d, 1d, ChoiceFormat.nextDouble(1d) },
					new String[] { "repositories: ", "repository: ", "repositories: " });
			MessageFormat messageFormat = new MessageFormat(
					"Failed to resolve MavenResource: {0}. Configured remote {1}: {2}");
			messageFormat.setFormat(1, pluralizer);
			String repos = properties.getRemoteRepositories().isEmpty()
					? "none"
					: StringUtils.collectionToDelimitedString(properties.getRemoteRepositories().keySet(), ",", "[", "]");
			throw new IllegalStateException(
					messageFormat.format(new Object[] { resource, properties.getRemoteRepositories().size(), repos }),
					e);
		}
		return toResource(resolvedArtifact);
	}

	private void validateCoordinates(MavenResource resource) {
		Assert.hasText(resource.getGroupId(), "groupId must not be blank.");
		Assert.hasText(resource.getArtifactId(), "artifactId must not be blank.");
		Assert.hasText(resource.getExtension(), "extension must not be blank.");
		Assert.hasText(resource.getVersion(), "version must not be blank.");
	}

	public FileSystemResource toResource(ArtifactResult resolvedArtifact) {
		return new FileSystemResource(resolvedArtifact.getArtifact().getFile());
	}

	public Artifact toJarArtifact(MavenResource resource) {
		return toArtifact(resource, resource.getExtension());
	}

	public Artifact toPomArtifact(MavenResource resource) {
		return toArtifact(resource, "pom");
	}

	public Artifact toArtifact(MavenResource resource, String extension) {
		return new DefaultArtifact(resource.getGroupId(),
				resource.getArtifactId(),
				resource.getClassifier() != null ? resource.getClassifier() : "",
				extension,
				resource.getVersion());
	}

	public VersionResult version(String coordinates) {
		Assert.notNull(coordinates, "coordinates must not be null");
		return this.version(MavenResource.parse(coordinates, properties));
	}

	public VersionResult version(String groupId, String artifactId, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.version(resource);
	}

	public VersionResult version(String groupId, String artifactId, String classifier, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.version(resource);
	}

	public VersionResult version(String groupId, String artifactId, String classifier, String version, String extension) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).extension(extension).build();
		return this.version(resource);
	}
	
	/**
	 * get VersionResult
	 * @author 		： <a href="https://github.com/vindell">vindell</a>
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link VersionResult} representing the resolved artifact in the local repository
	 * @throws IllegalStateException if the artifact does not exist or the resolution fails
	 */
	public VersionResult version(MavenResource resource) {
		
		Assert.notNull(resource, "MavenResource must not be null");
		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem,
				properties, authentication);
		validateCoordinates(resource);
		
		try {
			
			VersionRequest request = new VersionRequest(toJarArtifact(resource), this.remoteRepositories,
					JavaScopes.RUNTIME);
			
			return this.repositorySystem.resolveVersion(session, request);
			
		} catch (VersionResolutionException e) {
			ChoiceFormat pluralizer = new ChoiceFormat(
					new double[] { 0d, 1d, ChoiceFormat.nextDouble(1d) },
					new String[] { "repositories: ", "repository: ", "repositories: " });
			MessageFormat messageFormat = new MessageFormat(
					"Failed to resolve MavenResource: {0}. Configured remote {1}: {2}");
			messageFormat.setFormat(1, pluralizer);
			String repos = properties.getRemoteRepositories().isEmpty()
					? "none"
					: StringUtils.collectionToDelimitedString(properties.getRemoteRepositories().keySet(), ",", "[", "]");
			throw new IllegalStateException(
					messageFormat.format(new Object[] { resource, properties.getRemoteRepositories().size(), repos }),
					e);
		}
	}

	public VersionRangeResult versionRange(String coordinates) {
		Assert.notNull(coordinates, "coordinates must not be null");
		return this.versionRange(MavenResource.parse(coordinates, properties));
	}

	public VersionRangeResult versionRange(String groupId, String artifactId, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId).version(version)
				.build();
		return this.versionRange(resource);
	}

	public VersionRangeResult versionRange(String groupId, String artifactId, String classifier, String version) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).build();
		return this.versionRange(resource);
	}

	public VersionRangeResult versionRange(String groupId, String artifactId, String classifier, String version, String extension) {
		MavenResource resource = new MavenResource.Builder().groupId(groupId).artifactId(artifactId)
				.classifier(classifier).version(version).extension(extension).build();
		return this.versionRange(resource);
	}
	
	/**
	 * get VersionRangeResult
	 * @author 		： <a href="https://github.com/vindell">vindell</a>
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link VersionRangeResult} representing the resolved artifact in the local repository
	 * @throws IllegalStateException if the artifact does not exist or the resolution fails
	 */
	public VersionRangeResult versionRange(MavenResource resource) {
		
		Assert.notNull(resource, "MavenResource must not be null");
		RepositorySystemSession session = RepositorySystemUtils.newRepositorySystemSession(this.repositorySystem,
				properties, authentication);
		validateCoordinates(resource);
		
		try {
			
			AbstractArtifact artifact = new DefaultArtifact(resource.getGroupId() + ":" + resource.getArtifactId() + ":[0,)");

			VersionRangeRequest rangeRequest = new VersionRangeRequest(artifact, this.remoteRepositories,
					JavaScopes.RUNTIME);
			
			return this.repositorySystem.resolveVersionRange(session, rangeRequest);
			
		} catch (VersionRangeResolutionException e) {
			ChoiceFormat pluralizer = new ChoiceFormat(
					new double[] { 0d, 1d, ChoiceFormat.nextDouble(1d) },
					new String[] { "repositories: ", "repository: ", "repositories: " });
			MessageFormat messageFormat = new MessageFormat(
					"Failed to resolve MavenResource: {0}. Configured remote {1}: {2}");
			messageFormat.setFormat(1, pluralizer);
			String repos = properties.getRemoteRepositories().isEmpty()
					? "none"
					: StringUtils.collectionToDelimitedString(properties.getRemoteRepositories().keySet(), ",", "[", "]");
			throw new IllegalStateException(
					messageFormat.format(new Object[] { resource, properties.getRemoteRepositories().size(), repos }),
					e);
		}
	}
	
}
