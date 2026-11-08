package am.yerevanhomes;

import org.springframework.boot.SpringApplication;

public class TestYerevanHomesApplication {

	public static void main(String[] args) {
		SpringApplication.from(YerevanHomesApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
