package am.yerevanhomes.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** Fills createdAt / updatedAt on documents. */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
