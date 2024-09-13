package org.apache.maven.spring.boot;

import java.util.HashMap;
import java.util.Map;

import org.apache.maven.spring.boot.ext.MavenClientTemplate;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.deployer.resource.maven.MavenProperties.RemoteRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({ LocalRepository.class, RepositoryConnectorFactory.class, FileTransporterFactory.class, HttpTransporterFactory.class })
@EnableConfigurationProperties({ MavenClientProperties.class })
public class MavenClientAutoConfiguration {
	
	@Bean
	@ConfigurationProperties("maven.settings.remote-repositories")
	public  Map<String, RemoteRepository> remoteRepositories(){
		 return new HashMap<>();
	};
	
	
	@Bean
	public MavenClientTemplate mavenInvokerTemplate(MavenClientProperties mavenProperties) {
		return new MavenClientTemplate(mavenProperties);
	}

}
