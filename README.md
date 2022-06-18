# maven-client-spring-boot-starter
Spring Boot Starter For Maven Client

### 说明

 > https://www.cnblogs.com/xiaosiyuan/articles/5887642.html

### Maven

``` xml
<dependency>
	<groupId>com.github.hiwepy</groupId>
	<artifactId>maven-client-spring-boot-starter</artifactId>
	<version>2.0.0.RELEASE</version>
</dependency>
```

### Sample

```java

import javax.annotation.PostConstruct;

import org.apache.maven.spring.boot.MavenClientTemplate_Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
	
	@Autowired
	private MavenClientTemplate clientTemplate;
	
	@PostConstruct
	private void init() {
		
		Resource resource = clientTemplate.resolve(coordinates);
		System.out.println("Description:" + resource.getDescription());
		System.out.println("Filename:" + resource.getFilename());
		
	}
	
	public static void main(String[] args) throws Exception {
		SpringApplication.run(Application.class, args);
	}

}

```
